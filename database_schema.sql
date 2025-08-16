-- Voting System Database Schema for PostgreSQL

-- Create database
CREATE DATABASE voting_system;

-- Connect to the database
\c voting_system;

-- Users table
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Features table
CREATE TABLE features (
    id SERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    user_id INTEGER NOT NULL,
    vote_count INTEGER DEFAULT 0,
    status VARCHAR(20) DEFAULT 'open',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Votes table (to track who voted for what)
CREATE TABLE votes (
    id SERIAL PRIMARY KEY,
    user_id INTEGER NOT NULL,
    feature_id INTEGER NOT NULL,
    vote_type INTEGER DEFAULT 1, -- 1 for upvote, -1 for downvote (if needed later)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (feature_id) REFERENCES features(id) ON DELETE CASCADE,
    UNIQUE(user_id, feature_id) -- Prevent duplicate votes
);

-- Create indexes for better performance
CREATE INDEX idx_features_user_id ON features(user_id);
CREATE INDEX idx_features_vote_count ON features(vote_count DESC);
CREATE INDEX idx_votes_user_id ON votes(user_id);
CREATE INDEX idx_votes_feature_id ON votes(feature_id);

-- Trigger to update vote_count when a vote is added
CREATE OR REPLACE FUNCTION update_vote_count()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE features 
        SET vote_count = vote_count + NEW.vote_type 
        WHERE id = NEW.feature_id;
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE features 
        SET vote_count = vote_count - OLD.vote_type 
        WHERE id = OLD.feature_id;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER vote_count_trigger
AFTER INSERT OR DELETE ON votes
FOR EACH ROW
EXECUTE FUNCTION update_vote_count();

-- Trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_features_updated_at BEFORE UPDATE ON features
FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();