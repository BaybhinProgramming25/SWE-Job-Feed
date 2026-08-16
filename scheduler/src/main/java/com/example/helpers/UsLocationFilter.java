package com.example.helpers;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Geo gate: keeps only postings that carry an explicit United States signal.
 *
 * A posting passes when it names a US country marker ("US", "United States"),
 * a US state by full name, or a well-known US city -- these are treated as a
 * "strong" signal. A bare two-letter state code ("Austin, TX") is a "weak"
 * signal: it passes only when nothing clearly foreign appears alongside it,
 * which keeps codes like DE (Delaware) from leaking in European postings.
 *
 * Anything with no recognizable US signal -- blank, "Remote", "Hybrid",
 * "2 Locations", or a foreign country/city -- is rejected.
 */
public final class UsLocationFilter {

    private UsLocationFilter() {}

    // "us" / "u.s." / "usa" / "u.s.a." as a standalone token (not inside "houston").
    private static final Pattern US_MARKER =
            Pattern.compile("(^|[^a-z])u\\.?s\\.?a?\\.?([^a-z]|$)");

    // "City, ST" style two-letter state codes. Anchored to a preceding comma so
    // common words that collide with state codes ("in" in "In-Office", "or",
    // "me", "hi", ...) are not mistaken for a state.
    private static final Pattern STATE_ABBREV = Pattern.compile(
            ",\\s*(al|ak|az|ar|ca|co|ct|de|fl|ga|hi|ia|id|il|in|ks|ky|la|ma|md|me|"
          + "mi|mn|mo|ms|mt|nc|nd|ne|nh|nj|nm|nv|ny|oh|ok|or|pa|ri|sc|sd|tn|tx|"
          + "ut|va|vt|wa|wi|wv|wy|dc)\\b");

    // Foreign country codes that do not collide with any US state abbreviation.
    private static final Pattern FOREIGN_CODE = Pattern.compile(
            "\\b(uk|gb|ie|fr|es|it|nl|be|se|dk|fi|pl|cz|pt|gr|au|nz|jp|cn|kr|sg|"
          + "il|za|tr|ph|th|vn|my|at|ch)\\b");

    private static final List<String> US_STATES = List.of(
            "alabama", "alaska", "arizona", "arkansas", "california", "colorado",
            "connecticut", "delaware", "florida", "georgia", "hawaii", "idaho",
            "illinois", "indiana", "iowa", "kansas", "kentucky", "louisiana",
            "maine", "maryland", "massachusetts", "michigan", "minnesota",
            "mississippi", "missouri", "montana", "nebraska", "nevada",
            "new hampshire", "new jersey", "new mexico", "new york",
            "north carolina", "north dakota", "ohio", "oklahoma", "oregon",
            "pennsylvania", "rhode island", "south carolina", "south dakota",
            "tennessee", "texas", "utah", "vermont", "virginia", "washington",
            "west virginia", "wisconsin", "wyoming", "district of columbia");

    private static final List<String> US_CITIES = List.of(
            "new york", "brooklyn", "manhattan", "san francisco", "mountain view",
            "palo alto", "menlo park", "santa clara", "san jose", "sunnyvale",
            "seattle", "bellevue", "redmond", "austin", "dallas", "houston",
            "san antonio", "chicago", "boston", "cambridge", "denver", "boulder",
            "atlanta", "miami", "los angeles", "san diego", "phoenix",
            "salt lake", "pittsburgh", "philadelphia", "minneapolis", "detroit",
            "nashville", "raleigh", "durham", "charlotte", "columbus",
            "san mateo", "culver city", "irvine", "plano", "reston", "herndon",
            "bentonville", "westford", "arlington");

    // Foreign country and city names. Used only to veto a weak (abbreviation)
    // match, so entries here should be unambiguous non-US place names.
    private static final List<String> FOREIGN_NAMES = List.of(
            // countries
            "germany", "deutschland", "austria", "switzerland", "netherlands",
            "belgium", "france", "spain", "portugal", "italy", "ireland",
            "united kingdom", "england", "scotland", "wales", "sweden",
            "norway", "denmark", "finland", "iceland", "poland", "czech",
            "slovakia", "hungary", "romania", "bulgaria", "greece", "croatia",
            "serbia", "ukraine", "russia", "turkey", "israel", "saudi",
            "emirates", "qatar", "egypt", "morocco", "nigeria", "kenya",
            "ghana", "south africa", "canada", "mexico", "brazil", "argentina",
            "chile", "colombia", "peru", "uruguay", "ecuador", "australia",
            "new zealand", "india", "pakistan", "bangladesh", "sri lanka",
            "china", "hong kong", "taiwan", "japan", "korea", "singapore",
            "malaysia", "indonesia", "philippines", "vietnam", "thailand",
            // cities
            "london", "manchester", "birmingham", "glasgow", "edinburgh",
            "dublin", "cork", "paris", "lyon", "berlin", "munich", "muenchen",
            "cologne", "koln", "hamburg", "frankfurt", "stuttgart",
            "dusseldorf", "koblenz", "amsterdam", "rotterdam", "brussels",
            "madrid", "barcelona", "lisbon", "milan", "rome", "zurich",
            "geneva", "vienna", "stockholm", "oslo", "copenhagen", "helsinki",
            "warsaw", "prague", "budapest", "athens", "tel aviv", "dubai",
            "cairo", "lagos", "nairobi", "johannesburg", "cape town", "toronto",
            "montreal", "ottawa", "guadalajara", "monterrey", "sao paulo",
            "rio de janeiro", "buenos aires", "santiago", "bogota", "lima",
            "sydney", "melbourne", "brisbane", "auckland", "bengaluru",
            "bangalore", "mumbai", "delhi", "hyderabad", "chennai", "pune",
            "gurgaon", "noida", "karachi", "dhaka", "beijing", "shanghai",
            "shenzhen", "tokyo", "osaka", "seoul", "kuala lumpur", "jakarta",
            "manila", "bangkok", "ho chi minh", "hanoi", "moncton");

    public static boolean isUsa(String location) {
        if (location == null) return false;
        String loc = location.toLowerCase().strip();
        if (loc.isEmpty()) return false;

        // Strong signal: an explicit country marker, a state name, or a US city.
        boolean strongUs = US_MARKER.matcher(loc).find()
                || loc.contains("united states")
                || containsAny(loc, US_STATES)
                || containsAny(loc, US_CITIES);
        if (strongUs) return true;

        // Weak signal: a bare state code, accepted only when nothing foreign is
        // present to explain it away (e.g. "Berlin, DE" -> Germany, not Delaware).
        boolean weakUs = STATE_ABBREV.matcher(loc).find();
        if (!weakUs) return false;

        boolean foreign = containsAny(loc, FOREIGN_NAMES)
                || FOREIGN_CODE.matcher(loc).find();
        return !foreign;
    }

    private static boolean containsAny(String haystack, List<String> needles) {
        for (String n : needles) {
            if (haystack.contains(n)) return true;
        }
        return false;
    }
}
