package com.example.helpers;
 
import com.example.model.JobFound;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
 
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;


@Component 
public class AtsFetchers {

    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    private static final String USER_AGENT = "personal-job-alert-bot/1.0 (+polling public ATS feeds, low volume)";

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private final ObjectMapper mapper = new ObjectMapper();


    public List<JobFound> fetch(String ats, String slug) throws Exception {
        
        return switch(ats) {
            case "greenhouse" -> greenhouse(slug);
            case "lever" -> lever(slug);
            case "ashby" -> ashby(slug);
            case "smartrecruiters" -> smartrecruiters(slug);
            case "recruitee" -> recruitee(slug);
            case "workable" ->  workable(slug);
            case "teamtailor" -> teamtailor(slug);
            case "workday" -> workday(slug);
            case "remotive" -> remotive(slug);
            case "remoteok" -> remoteOk();
            case "arbeitnow" -> arbeitnow();
            case "amazon" -> amazon();
            case "netflix" -> netflix();
            default -> throw new IllegalArgumentException("Unknown ATS: " + ats);
        };
    }

    private JsonNode getJson(String url) throws Exception {

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET().build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " from " + url);
        }

        return mapper.readTree(resp.body());
    }
    
    // Only workable requires a POST request in order to retrieve job information 
    private JsonNode postJson(String url, String body) throws Exception {

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() >= 400) {
            throw new RuntimeException("HTTP " + resp.statusCode() + " from " + url);
        }

        return mapper.readTree(resp.body());
    }
 
    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return (v == null || v.isNull()) ? "" : v.asText();
    }

    // Different fetchers
    private List<JobFound> greenhouse(String slug) throws Exception {
        JsonNode data = getJson("https://boards-api.greenhouse.io/v1/boards/"
                + slug + "/jobs?content=true");
        List<JobFound> out = new ArrayList<>();
        for (JsonNode j : data.path("jobs")) {
            StringJoiner dept = new StringJoiner(", ");
            for (JsonNode d : j.path("departments")) dept.add(text(d, "name"));
            out.add(JobFound.of(slug, "greenhouse", text(j, "id"),
                    text(j, "title"), j.path("location").path("name").asText(""),
                    dept.toString(), text(j, "absolute_url"), text(j, "updated_at")));
        }
        return out;
    }
 
    private List<JobFound> lever(String slug) throws Exception {
        JsonNode data = getJson("https://api.lever.co/v0/postings/" + slug + "?mode=json");
        List<JobFound> out = new ArrayList<>();
        for (JsonNode j : data) {
            JsonNode cats = j.path("categories");
            String dept = !text(cats, "team").isEmpty()
                    ? text(cats, "team") : text(cats, "department");
            out.add(JobFound.of(slug, "lever", text(j, "id"), text(j, "text"),
                    text(cats, "location"), dept,
                    text(j, "hostedUrl"), text(j, "createdAt")));
        }
        return out;
    }
 
    private List<JobFound> ashby(String slug) throws Exception {
        JsonNode data = getJson("https://api.ashbyhq.com/posting-api/job-board/"
                + slug + "?includeCompensation=true");
        List<JobFound> out = new ArrayList<>();
        for (JsonNode j : data.path("jobs")) {
            String dept = !text(j, "department").isEmpty()
                    ? text(j, "department") : text(j, "team");
            out.add(JobFound.of(slug, "ashby", text(j, "id"), text(j, "title"),
                    text(j, "location"), dept,
                    text(j, "jobUrl"), text(j, "publishedAt")));
        }
        return out;
    }
 
    private List<JobFound> smartrecruiters(String slug) throws Exception {
        List<JobFound> out = new ArrayList<>();
        int offset = 0, limit = 100;
        while (true) {
            JsonNode data = getJson("https://api.smartrecruiters.com/v1/companies/"
                    + slug + "/postings?limit=" + limit + "&offset=" + offset);
            JsonNode content = data.path("content");
            for (JsonNode j : content) {
                JsonNode loc = j.path("location");
                StringJoiner locStr = new StringJoiner(", ");
                for (String f : List.of("city", "region", "country")) {
                    String v = text(loc, f);
                    if (!v.isEmpty()) locStr.add(v);
                }
                String dept = j.path("department").path("label").asText(
                        j.path("function").path("label").asText(""));
                String id = text(j, "id");
                out.add(JobFound.of(slug, "smartrecruiters", id, text(j, "name"),
                        locStr.toString(), dept,
                        "https://jobs.smartrecruiters.com/" + slug + "/" + id,
                        text(j, "releasedDate")));
            }
            offset += limit;
            if (content.size() < limit || offset > 2000) break;
            Thread.sleep(400);
        }
        return out;
    }
 
    private List<JobFound> recruitee(String slug) throws Exception {
        JsonNode data = getJson("https://" + slug + ".recruitee.com/api/offers/");
        List<JobFound> out = new ArrayList<>();
        for (JsonNode j : data.path("offers")) {
            String loc = !text(j, "location").isEmpty()
                    ? text(j, "location") : text(j, "city");
            String url = !text(j, "careers_url").isEmpty()
                    ? text(j, "careers_url") : text(j, "careers_apply_url");
            out.add(JobFound.of(slug, "recruitee", text(j, "id"), text(j, "title"),
                    loc, text(j, "department"), url, text(j, "published_at")));
        }
        return out;
    }
 
    private List<JobFound> workable(String slug) throws Exception {
        JsonNode data = getJson("https://apply.workable.com/api/v1/widget/accounts/"
                + slug + "?details=true");
        List<JobFound> out = new ArrayList<>();
        for (JsonNode j : data.path("jobs")) {
            StringJoiner loc = new StringJoiner(", ");
            for (String f : List.of("city", "state", "country")) {
                String v = text(j, f);
                if (!v.isEmpty()) loc.add(v);
            }
            String url = !text(j, "url").isEmpty()
                    ? text(j, "url") : text(j, "application_url");
            out.add(JobFound.of(slug, "workable", text(j, "shortcode"),
                    text(j, "title"), loc.toString(), text(j, "department"),
                    url, text(j, "published_on")));
        }
        return out;
    }
 
    private List<JobFound> teamtailor(String slug) throws Exception {
        JsonNode data = getJson("https://" + slug + ".teamtailor.com/jobs.json");
        List<JobFound> out = new ArrayList<>();
        // Current career sites serve JSON Feed: postings live in "items"
        // with only id/title/url/date_published (no location or department)
        if (data.has("items")) {
            for (JsonNode j : data.path("items")) {
                out.add(JobFound.of(slug, "teamtailor", text(j, "id"), text(j, "title"),
                        "", "", text(j, "url"), text(j, "date_published")));
            }
            return out;
        }
        JsonNode items = data.isArray() ? data : data.path("jobs");
        for (JsonNode j : items) {
            String url = !text(j, "url").isEmpty()
                    ? text(j, "url") : text(j, "careersite_job_url");
            out.add(JobFound.of(slug, "teamtailor", text(j, "id"), text(j, "title"),
                    text(j, "location"), text(j, "department"),
                    url, text(j, "created_at")));
        }
        return out;
    }
 
    private List<JobFound> workday(String slugJson) throws Exception {
        JsonNode cfg = mapper.readTree(slugJson);
        String tenant = text(cfg, "tenant"), dc = text(cfg, "dc"), site = text(cfg, "site");
        String base = "https://" + tenant + "." + dc + ".myworkdayjobs.com/wday/cxs/"
                + tenant + "/" + site + "/jobs";
        List<JobFound> out = new ArrayList<>();
        int offset = 0, limit = 20;
        while (true) {
            String payload = "{\"appliedFacets\":{},\"limit\":" + limit
                    + ",\"offset\":" + offset + ",\"searchText\":\"\"}";
            JsonNode data = postJson(base, payload);
            JsonNode postings = data.path("jobPostings");
            for (JsonNode j : postings) {
                String path = text(j, "externalPath");
                out.add(JobFound.of(tenant, "workday", path, text(j, "title"),
                        text(j, "locationsText"), "",
                        "https://" + tenant + "." + dc + ".myworkdayjobs.com/" + site + path,
                        text(j, "postedOn")));
            }
            offset += limit;
            if (offset >= data.path("total").asInt(0) || postings.isEmpty() || offset > 1000) break;
            Thread.sleep(500);
        }
        return out;
    }
 
    private List<JobFound> remotive(String category) throws Exception {
        JsonNode data = getJson("https://remotive.com/api/remote-jobs?category=" + category);
        List<JobFound> out = new ArrayList<>();
        for (JsonNode j : data.path("jobs")) {
            out.add(JobFound.of(text(j, "company_name"), "remotive", text(j, "id"),
                    text(j, "title"), text(j, "candidate_required_location"),
                    text(j, "category"), text(j, "url"), text(j, "publication_date")));
        }
        return out;
    }
 
    private List<JobFound> remoteOk() throws Exception {
        JsonNode data = getJson("https://remoteok.com/api");
        List<JobFound> out = new ArrayList<>();
        for (JsonNode j : data) {
            if (!j.isObject() || text(j, "position").isEmpty()) continue;  // first element is metadata
            StringJoiner tags = new StringJoiner(", ");
            for (JsonNode t : j.path("tags")) tags.add(t.asText());
            String loc = text(j, "location").isEmpty() ? "Remote" : text(j, "location");
            out.add(JobFound.of(text(j, "company"), "remoteok", text(j, "id"),
                    text(j, "position"), loc, tags.toString(),
                    text(j, "url"), text(j, "date")));
        }
        return out;
    }
 
    private List<JobFound> arbeitnow() throws Exception {
        JsonNode data = getJson("https://www.arbeitnow.com/api/job-board-api");
        List<JobFound> out = new ArrayList<>();
        for (JsonNode j : data.path("data")) {
            StringJoiner tags = new StringJoiner(", ");
            for (JsonNode t : j.path("tags")) tags.add(t.asText());
            out.add(JobFound.of(text(j, "company_name"), "arbeitnow", text(j, "slug"),
                    text(j, "title"), text(j, "location"), tags.toString(),
                    text(j, "url"), text(j, "created_at")));
        }
        return out;
    }

    // Amazon self-hosts on amazon.jobs, which exposes a public JSON search.
    // Results are sorted newest-first so the freshness filter can stop early.
    private List<JobFound> amazon() throws Exception {
        List<JobFound> out = new ArrayList<>();
        int offset = 0, limit = 100;
        while (true) {
            JsonNode data = getJson("https://www.amazon.jobs/en/search.json"
                    + "?base_query=software+engineer&country=USA&sort=recent"
                    + "&result_limit=" + limit + "&offset=" + offset);
            JsonNode jobs = data.path("jobs");
            if (jobs.isEmpty()) break;
            for (JsonNode j : jobs) {
                String path = text(j, "job_path");
                String url = path.startsWith("http") ? path : "https://www.amazon.jobs" + path;
                out.add(JobFound.of("Amazon", "amazon", text(j, "id_icims"),
                        text(j, "title"), text(j, "normalized_location"),
                        text(j, "job_category"), url, usDateToIso(text(j, "posted_date"))));
            }
            offset += limit;
            if (offset >= data.path("hits").asInt(0) || offset >= 500) break;
            Thread.sleep(500);
        }
        return out;
    }

    // Netflix self-hosts on an Eightfold-powered board with a public JSON API.
    // t_update is a unix epoch (seconds), which the poller parses directly.
    private List<JobFound> netflix() throws Exception {
        List<JobFound> out = new ArrayList<>();
        int start = 0, num = 100;
        while (true) {
            JsonNode data = getJson("https://explore.jobs.netflix.net/api/apply/v2/jobs"
                    + "?domain=netflix.com&query=software+engineer&location=United+States"
                    + "&sort_by=recency&num=" + num + "&start=" + start);
            JsonNode positions = data.path("positions");
            if (positions.isEmpty()) break;
            for (JsonNode j : positions) {
                out.add(JobFound.of("Netflix", "netflix", text(j, "id"),
                        text(j, "name"), text(j, "location"), text(j, "department"),
                        text(j, "canonicalPositionUrl"), text(j, "t_update")));
            }
            start += num;
            if (start >= data.path("count").asInt(0) || start >= 500) break;
            Thread.sleep(500);
        }
        return out;
    }

    // amazon.jobs prints dates like "August 15, 2026"; convert to ISO so the
    // poller's freshness check (which understands ISO dates) can read it.
    private static String usDateToIso(String date) {
        if (date == null || date.isBlank()) return "";
        try {
            return java.time.LocalDate
                    .parse(date.strip(), java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.US))
                    .toString();
        } catch (Exception e) {
            return date;
        }
    }
}