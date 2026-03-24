-- ─────────────────────────────────────────────────────────────────────────────
-- V7: Grants Application System
-- A sample domain for demonstrating Text-to-SQL natural language queries.
-- ─────────────────────────────────────────────────────────────────────────────

-- ── Grant Programs ───────────────────────────────────────────────────────────
CREATE TABLE grant_programs (
    id               SERIAL PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    description      TEXT,
    funding_body     VARCHAR(200) NOT NULL,
    total_budget     NUMERIC(15, 2) NOT NULL,
    available_budget NUMERIC(15, 2) NOT NULL,
    max_award        NUMERIC(15, 2),
    deadline         DATE NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',  -- OPEN, CLOSED, AWARDED
    category         VARCHAR(100),
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

-- ── Applicant Organisations ──────────────────────────────────────────────────
CREATE TABLE grant_applicants (
    id            SERIAL PRIMARY KEY,
    org_name      VARCHAR(200) NOT NULL,
    contact_name  VARCHAR(100) NOT NULL,
    contact_email VARCHAR(150) NOT NULL,
    org_type      VARCHAR(50)  NOT NULL,  -- NGO, SME, UNIVERSITY, GOVT
    country       VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

-- ── Grant Applications ───────────────────────────────────────────────────────
CREATE TABLE grant_applications (
    id               SERIAL PRIMARY KEY,
    program_id       INTEGER      NOT NULL REFERENCES grant_programs(id),
    applicant_id     INTEGER      NOT NULL REFERENCES grant_applicants(id),
    title            VARCHAR(300) NOT NULL,
    description      TEXT,
    requested_amount NUMERIC(15, 2) NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',  -- SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED, WITHDRAWN
    submitted_at     TIMESTAMP    NOT NULL DEFAULT now(),
    decision_at      TIMESTAMP,
    decision_notes   TEXT
);

-- ── Reviewers ────────────────────────────────────────────────────────────────
CREATE TABLE grant_reviewers (
    id         SERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    expertise  VARCHAR(200),
    department VARCHAR(100)
);

-- ── Reviews ──────────────────────────────────────────────────────────────────
CREATE TABLE grant_reviews (
    id             SERIAL PRIMARY KEY,
    application_id INTEGER     NOT NULL REFERENCES grant_applications(id),
    reviewer_id    INTEGER     NOT NULL REFERENCES grant_reviewers(id),
    score          INTEGER     NOT NULL CHECK (score BETWEEN 1 AND 10),
    recommendation VARCHAR(20) NOT NULL,  -- APPROVE, REJECT, MORE_INFO
    comments       TEXT,
    reviewed_at    TIMESTAMP   NOT NULL DEFAULT now()
);

-- ── Disbursements ────────────────────────────────────────────────────────────
CREATE TABLE grant_disbursements (
    id             SERIAL PRIMARY KEY,
    application_id INTEGER        NOT NULL REFERENCES grant_applications(id),
    amount         NUMERIC(15, 2) NOT NULL,
    disbursed_at   DATE           NOT NULL,
    reference_no   VARCHAR(100),
    notes          TEXT
);

-- ─────────────────────────────────────────────────────────────────────────────
-- Seed data
-- ─────────────────────────────────────────────────────────────────────────────

-- Grant Programs
INSERT INTO grant_programs (name, description, funding_body, total_budget, available_budget, max_award, deadline, status, category) VALUES
  ('Green Innovation Fund',
   'Supports R&D projects aimed at reducing carbon emissions in manufacturing',
   'Ministry of Environment',
   5000000.00, 1200000.00, 500000.00,
   '2025-06-30', 'OPEN', 'Environment'),

  ('Digital Transformation SME Grant',
   'Accelerate digital adoption for small and medium enterprises',
   'Economic Development Board',
   3000000.00, 800000.00, 150000.00,
   '2025-09-15', 'OPEN', 'Technology'),

  ('Community Health Initiative',
   'Fund grassroots health programmes in underserved communities',
   'Ministry of Health',
   2000000.00, 0.00, 200000.00,
   '2024-12-31', 'CLOSED', 'Healthcare'),

  ('Research Excellence Award',
   'Advance cutting-edge academic research with societal impact',
   'National Research Foundation',
   8000000.00, 3500000.00, 1000000.00,
   '2025-12-01', 'OPEN', 'Research'),

  ('Youth Entrepreneurship Scheme',
   'Seed funding for entrepreneurs under 35 launching their first venture',
   'Enterprise Singapore',
   1500000.00, 600000.00, 50000.00,
   '2025-07-31', 'OPEN', 'Entrepreneurship');

-- Applicant Organisations
INSERT INTO grant_applicants (org_name, contact_name, contact_email, org_type, country) VALUES
  ('EcoTech Solutions Pte Ltd',        'Alice Tan',       'alice@ecotech.sg',       'SME',        'Singapore'),
  ('Clean Energy Consortium',          'Bob Lim',         'bob@cec.org.sg',         'NGO',        'Singapore'),
  ('National University of Singapore', 'Prof Chen Wei',   'chen@nus.edu.sg',        'UNIVERSITY', 'Singapore'),
  ('BrightStart Community Services',   'Diana Raj',       'diana@brightstart.org',  'NGO',        'Singapore'),
  ('MedCore Research Institute',       'Dr Eric Ng',      'eric@medcore.sg',        'SME',        'Singapore'),
  ('Nanyang Technological University', 'Prof Liao Fang',  'liao@ntu.edu.sg',        'UNIVERSITY', 'Singapore'),
  ('SmartBuild Innovations',           'Grace Ho',        'grace@smartbuild.com',   'SME',        'Singapore'),
  ('Youth Forward Collective',         'Henry Chua',      'henry@youthfwd.org',     'NGO',        'Singapore'),
  ('DataSpark Analytics',              'Irene Kwon',      'irene@dataspark.io',     'SME',        'Singapore'),
  ('GovTech Research Lab',             'James Phua',      'james@govtechlab.gov.sg','GOVT',       'Singapore');

-- Grant Applications
INSERT INTO grant_applications (program_id, applicant_id, title, description, requested_amount, status, submitted_at, decision_at, decision_notes) VALUES
  -- Green Innovation Fund (id=1)
  (1, 1, 'Carbon-Neutral Manufacturing Line',
   'Install AI-driven energy optimisation in factory floor operations',
   450000.00, 'APPROVED', '2025-01-10 09:00:00', '2025-03-05 14:00:00',
   'Strong technical merit; approved at full requested amount'),

  (1, 2, 'Renewable Energy Microgrid for Industrial Parks',
   'Pilot renewable microgrid for three industrial parks in Jurong',
   500000.00, 'UNDER_REVIEW', '2025-02-14 11:30:00', NULL, NULL),

  (1, 7, 'Smart Building Energy Management System',
   'IoT-based real-time energy monitoring and automated load balancing',
   320000.00, 'UNDER_REVIEW', '2025-03-01 08:45:00', NULL, NULL),

  -- Digital Transformation SME Grant (id=2)
  (2, 1, 'AI-Powered Supply Chain Optimisation',
   'Deploy ML models to predict demand and reduce inventory waste',
   140000.00, 'APPROVED', '2024-11-20 10:00:00', '2025-01-15 16:00:00',
   'Clear ROI projection; approved'),

  (2, 9, 'Cloud-Native ERP Replacement',
   'Migrate legacy on-prem ERP to cloud-native microservices architecture',
   150000.00, 'SUBMITTED', '2025-04-01 12:00:00', NULL, NULL),

  (2, 7, 'Digital Twin for Facility Management',
   'Create a 3D digital twin of facilities for predictive maintenance',
   130000.00, 'REJECTED', '2024-10-10 09:00:00', '2025-01-20 10:00:00',
   'Insufficient pilot data; resubmit with evidence of feasibility'),

  -- Community Health Initiative (id=3)
  (3, 4, 'Mobile Clinic Outreach Programme',
   'Weekly mobile clinic visits to elderly in rental housing estates',
   185000.00, 'APPROVED', '2024-08-05 08:00:00', '2024-10-01 09:00:00',
   'Fully aligned with programme objectives'),

  (3, 5, 'Chronic Disease Management Platform',
   'Digital platform connecting patients with community health workers',
   200000.00, 'APPROVED', '2024-07-20 14:00:00', '2024-09-15 11:00:00',
   'Approved at maximum award; excellent proposal'),

  -- Research Excellence Award (id=4)
  (4, 3, 'Quantum-Safe Cryptography for Financial Networks',
   'Develop post-quantum cryptographic protocols for Singapore banking sector',
   980000.00, 'APPROVED', '2025-01-05 09:30:00', '2025-04-10 15:00:00',
   'Outstanding academic pedigree; national strategic importance'),

  (4, 6, 'Next-Generation Battery Materials',
   'Research solid-state electrolytes for high-density EV batteries',
   1000000.00, 'UNDER_REVIEW', '2025-02-28 10:00:00', NULL, NULL),

  (4, 10, 'AI Ethics and Governance Framework',
   'Develop a regulatory framework for responsible AI deployment in ASEAN',
   750000.00, 'SUBMITTED', '2025-04-15 11:00:00', NULL, NULL),

  -- Youth Entrepreneurship Scheme (id=5)
  (5, 8, 'Peer Mental Health Support App',
   'Mobile platform connecting youth with trained peer counsellors',
   48000.00, 'APPROVED', '2025-01-25 10:00:00', '2025-03-10 14:00:00',
   'Innovative model; strong community need'),

  (5, 9, 'Hyperlocal Agri-Tech Marketplace',
   'Platform connecting urban vertical farms with restaurants and consumers',
   50000.00, 'UNDER_REVIEW', '2025-02-10 09:00:00', NULL, NULL),

  (5, 1, 'Green Packaging Subscription Box',
   'Subscription service delivering sustainably packaged everyday goods',
   45000.00, 'REJECTED', '2025-01-30 10:00:00', '2025-03-20 11:00:00',
   'Market size insufficient for fund objectives'),

  (5, 7, 'Smart Inventory SaaS for F&B Micro-Enterprises',
   'Lightweight SaaS for hawkers and small F&B operators to track stock',
   50000.00, 'SUBMITTED', '2025-04-05 08:00:00', NULL, NULL);

-- Reviewers
INSERT INTO grant_reviewers (name, email, expertise, department) VALUES
  ('Dr Siti Rahimah',   'siti@grants.gov.sg',   'Environmental Science, Renewable Energy', 'Environment Division'),
  ('Mr Tan Boon Keong', 'boonkeong@grants.gov.sg','Digital Transformation, FinTech',          'Technology Assessment'),
  ('Prof Wee Lay Bee',  'wee@grants.gov.sg',     'Public Health, Community Medicine',        'Health Division'),
  ('Dr Kevin Marsh',    'kmarsh@grants.gov.sg',  'Academic Research, Quantum Computing',     'Research Grants Office');

-- Reviews
INSERT INTO grant_reviews (application_id, reviewer_id, score, recommendation, comments, reviewed_at) VALUES
  (1, 1, 9, 'APPROVE',    'Technically robust plan with realistic milestones and clear emissions reduction metrics',              '2025-02-20 10:00:00'),
  (1, 2, 8, 'APPROVE',    'Solid business case; ROI model credible',                                                             '2025-02-22 14:30:00'),
  (2, 1, 7, 'MORE_INFO',  'Need clarity on grid interconnection approval timeline',                                              '2025-03-15 09:00:00'),
  (3, 1, 8, 'APPROVE',    'Comprehensive HVAC and lighting optimisation plan',                                                   '2025-03-20 11:00:00'),
  (4, 2, 9, 'APPROVE',    'Best in class ML supply chain proposal seen this cycle',                                              '2024-12-10 10:00:00'),
  (6, 2, 4, 'REJECT',     'Lacking baseline data; pilot should be run first',                                                   '2024-12-01 09:00:00'),
  (7, 3, 9, 'APPROVE',    'Directly addresses health equity gap; delivery model is proven',                                      '2024-09-10 10:00:00'),
  (8, 3, 10,'APPROVE',    'Exceptional proposal; digital health model is scalable and sustainable',                              '2024-08-30 14:00:00'),
  (9, 4, 10,'APPROVE',    'World-class research team; critical national priority',                                               '2025-03-15 10:00:00'),
  (9, 2, 9, 'APPROVE',    'Financial services impact is enormous; highly recommend full award',                                  '2025-03-18 11:00:00'),
  (10, 4, 8, 'MORE_INFO', 'Promising materials science; need to see more preliminary experimental data',                         '2025-04-01 09:00:00'),
  (12, 3, 8, 'APPROVE',   'Strong peer support model; cost-effective and scalable',                                              '2025-02-20 10:00:00'),
  (14, 2, 3, 'REJECT',    'Market is too niche and competitive; differentiation unclear',                                        '2025-02-25 14:00:00'),
  (5, 2, 6, 'MORE_INFO',  'Cloud architecture choice requires further justification; TCO analysis missing',                     '2025-04-10 09:00:00'),
  (13, 2, 7, 'MORE_INFO', 'Interesting vertical farming angle but supply chain logistics plan is underdeveloped',               '2025-03-05 10:00:00');

-- Disbursements
INSERT INTO grant_disbursements (application_id, amount, disbursed_at, reference_no, notes) VALUES
  (1,  225000.00, '2025-03-20', 'GIF-2025-001-A', 'First tranche: 50% on approval'),
  (1,  225000.00, '2025-06-20', 'GIF-2025-001-B', 'Second tranche: 50% on milestone completion'),
  (4,   70000.00, '2025-01-25', 'DTS-2025-004-A', 'First tranche: 50%'),
  (4,   70000.00, '2025-04-01', 'DTS-2025-004-B', 'Second tranche: 50%'),
  (7,  185000.00, '2024-10-15', 'CHI-2024-007',   'Single disbursement on approval'),
  (8,  200000.00, '2024-09-30', 'CHI-2024-008',   'Single disbursement on approval'),
  (9,  490000.00, '2025-04-20', 'REA-2025-009-A', 'First tranche: 50%'),
  (12,  48000.00, '2025-03-20', 'YES-2025-012',   'Full amount disbursed on approval');
