CREATE DATABASE IF NOT EXISTS dist_jobs_scheduler;

CREATE TABLE IF NOT EXISTS dist_jobs_scheduler.users(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone_number VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dist_jobs_scheduler.found_jobs(
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company VARCHAR(255),
    ats VARCHAR(32),
    jobId VARCHAR(255) UNIQUE,
    title TEXT,
    location TEXT,
    department TEXT,
    url TEXT,
    posted VARCHAR(64),
    firstSeen TIMESTAMPTZ NOT NULL DEFAULT now(),
    lastSeen TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dist_jobs_scheduler.watched_companies(
    user_id BIGINT NOT NULL REFERENCES dist_jobs_scheduler.users(id) ON DELETE CASCADE,
    company_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (company_name, user_id)
);

CREATE TABLE IF NOT EXISTS dist_jobs_scheduler.resumes(
    user_id BIGINT PRIMARY KEY REFERENCES dist_jobs_scheduler.users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    filename VARCHAR(255),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS dist_jobs_scheduler.applications(
    id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES dist_jobs_scheduler.users(id) ON DELETE CASCADE,
    company VARCHAR(255),
    title TEXT,
    location TEXT,
    url TEXT,
    ats VARCHAR(32),
    status VARCHAR(20) NOT NULL DEFAULT 'applied',   -- applied | interviewing | offer | rejected
    notes TEXT,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS applications_user_idx ON dist_jobs_scheduler.applications(user_id);
