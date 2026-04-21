-- Run once by PostgreSQL on container first boot
-- Creates isolated databases for every CodeSync service

CREATE DATABASE codesync_auth;
CREATE DATABASE codesync_project;
CREATE DATABASE codesync_file;
CREATE DATABASE codesync_collab;
CREATE DATABASE codesync_execution;
CREATE DATABASE codesync_version;
CREATE DATABASE codesync_comment;
CREATE DATABASE codesync_notification;