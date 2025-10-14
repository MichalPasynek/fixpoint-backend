SET search_path TO service, public;

CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);


CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL
);


CREATE TABLE IF NOT EXISTS user_roles (
    user_id UUID REFERENCES users(id) ON DELETE CASCADE NOT NULL,
    role_id INTEGER REFERENCES roles(id) ON DELETE CASCADE NOT NULL,
    PRIMARY KEY (user_id, role_id)
);


INSERT INTO roles (name) VALUES
('ADMIN'),
('TECHNICIAN'),
('CUSTOMERS')
ON CONFLICT (name) DO NOTHING;


INSERT INTO users (first_name, last_name, email, password) VALUES
('System', 'Admin', 'admin@service.com', 'password123')
ON CONFLICT (email) DO NOTHING;