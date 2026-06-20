# Claude Code Prompt Library — Currency Exchange Rate Service
### Architect Program Assignment — AI-Assisted Development Log

> This document captures the key prompts used across all development sessions to build this project using Claude Code. Prompts are grouped by phase. Phase 1 prompts were used on a prior machine; all others are captured from session transcripts.

---

## Phase 1 — Project Design & Scaffolding *(reconstructed)*

> These prompts were used on a prior laptop to initiate the project from the design documents in the `/docs` folder.

**Kickoff — project bootstrap from specs:**
> *"I have attached my architecture, data model, API contract, and functional requirements documents in the docs/ folder. Read all of them and scaffold a Spring Boot 3.3 + Java 21 project. Generate the pom.xml with all required dependencies: Spring Security, Spring Data JPA, Spring Cache, Resilience4j, Redis (Lettuce), Flyway, JWT (JJWT), SpringDoc OpenAPI, Lombok, and Testcontainers. Follow the package structure defined in the architecture doc."*

**Database migrations:**
> *"Refer to docs/data-model.md and create Flyway SQL migration scripts for all 4 tables: users, exchange_rates, transactions, and logs. Include all indexes, constraints, and enums exactly as defined in the data model. Name files V1 through V4."*

**Entity classes:**
> *"Refer to docs/data-model.md and create all 4 JPA entity classes: User, ExchangeRate, Transaction, Log. Use BigDecimal for all money and rate fields. Add JPA auditing annotations. Never use float or double for financial values."*

**Repositories:**
> *"Create Spring Data JPA repositories for all 4 entities. Add custom @Query methods needed for the API contract — for example, find transactions by user, find exchange rate by currency pair, find logs by event type and date range."*

**Auth module — register, login, JWT:**
> *"Refer to docs/api-contract.md and docs/architecture-summary.md. Implement the full authentication module: user registration with BCrypt (cost 12), login with JWT RS256 (15-min access token + 7-day refresh cookie), and the MFA OTP flow using Redis (5-min TTL, 6-digit OTP). JWT must not be issued until MFA verification is complete."*

**Spring Security config:**
> *"Implement the Spring Security filter chain as described in docs/architecture-summary.md. Add JwtAuthFilter, RateLimitFilter (Bucket4j, 100 req/min per user), CorrelationIdFilter, and CorsFilter in the correct order. Enforce the RBAC matrix from the architecture doc using @PreAuthorize on all endpoints."*

**Exchange rate module:**
> *"Implement the exchange rate module as per docs/functional-requirements.md (FR-01, FR-03, FR-06). Use the cache-aside pattern with Redis (key: rate:{FROM}:{TO}, TTL 5 min). Wrap the external Alpha Vantage API call with Resilience4j circuit breaker — 50% failure threshold, 30s wait, fallback to last cached rate."*

**Transaction module with approval workflow:**
> *"Implement the transaction/conversion module as per FR-04 and FR-12 in docs/functional-requirements.md. Conversions below the threshold should complete immediately with status=APPROVED. Conversions at or above the configurable threshold should get status=PENDING_APPROVAL and trigger email notifications to admin via SES. Admin approve/reject endpoints should notify the user by email."*

**Admin APIs:**
> *"Implement all admin REST endpoints as defined in docs/api-contract.md: user management (list, activate/deactivate, change role), exchange rate CRUD with cache invalidation, transaction approval workflow endpoints, and system log viewer. Enforce ADMIN role on all endpoints using @PreAuthorize."*

**Angular frontend scaffold:**
> *"Refer to docs/functional-requirements.md and docs/api-contract.md. Scaffold an Angular 18 project with Angular Material and Tailwind CSS. Create these modules: Auth (login, register, MFA verify), User Dashboard (currency converter, transaction history, profile), and Admin Dashboard (user management, rate management, transaction approval, system logs). Use a JWT interceptor to attach the Bearer token to every API call and handle 401 by redirecting to login."*

**Angular auth flow:**
> *"Implement the Angular authentication flow: login form → call POST /api/auth/login → if mfaRequired=true, redirect to MFA verify screen → on success, store access token in memory (not localStorage) and call the API with Authorization header. Implement token refresh using the refresh cookie on 401 responses."*

**Angular dashboard:**
> *"Build the currency converter component: currency pair dropdowns (populated from GET /api/exchange-rates), amount input, real-time rate display with auto-refresh every 30 seconds using RxJS interval + switchMap. On submit, call POST /api/exchange-rates/convert and show result. If status=PENDING_APPROVAL, show a pending banner instead of the converted amount."*

**Angular admin dashboard:**
> *"Build the admin dashboard with tabs for: Users (data table with activate/deactivate), Exchange Rates (CRUD with inline edit), Pending Approvals (approve/reject with confirmation dialog), All Transactions (filterable table), and System Logs (paginated log viewer). Use Angular Material table, dialog, and snackbar components."*

---

## Phase 2 — Local Development Environment (New Machine Setup)

- *"I want to compile both Java and UI code in this machine"*
- *"Maven is present in C:\The Maven 3.9.11\apache-maven-3.9.11 — start building backend"*
- *"I don't have Postgres. Use H2"*
- *"I was able to run the app in local in my earlier machine with same code without Postgres being installed — the changes you're suggesting shouldn't impact AWS Beanstalk deployment"*
- *"Add a Flyway seed migration (V5__seed_users.sql) that auto-creates both admin and non-admin users on startup"*
- *"Is there any default user ID for both admin and non-admin users?"*

---

## Phase 3 — AWS Elastic Beanstalk Deployment

- *"For POC, I am taking build in local and uploading in ELB using AWS Console — should I use AWS CodeBuild or CodePipeline?"*
- *"After recent deployment in ELB, login page is loading but getting 502 Bad Gateway error while logging in"*
- *"I deployed code and tried to login as admin — as per log MFA OTP sent but I didn't receive it. SES is using the same email for from address — is that a problem?"*
- *"Can you bypass MFA for admin user ID which is cartkn.kkdi@gmail.com — let me know if it's a good idea for a POC"*
- *"Can we update code to get a standard OTP for this email ID so that I can move to the next screen"*
- *"I am getting an error during login today — 504 Gateway Timeout"* → resolved → *"Shall I tell you something? You are the best. It worked."*
- *"Quick question — I deployed my UI in S3 with CloudFront and backend in Elastic Beanstalk — don't we need API Gateway in this setup?"*

---

## Phase 4 — Feature: Configurable Approval Threshold

- *"I want to add a new functionality to this app. Currently there is a $100 threshold above which conversion will go for Admin approval. I want this limit to be configurable by Admin instead of hardcoded in code. Make changes for the same."*
- *"Did you add JUnit test for above change? Else add — then run JUnit tests for this project"*
- *"Transactions ≥ $100 require admin approval — this message always shows $100. Amount should be dynamic based on what admin sets"*
- *"The approval threshold amount is already available to normal users, isn't it? Then how are you validating the conversion amount — if it's already available, let's use it to fix this message"*

---

## Phase 5 — Testing

### Backend — JUnit & JaCoCo
- *"This repo doesn't have much JUnit tests. Can you start adding JUnits for this project?"*
- *"Let's do test coverage run using JaCoCo"*
- *"Run Maven code coverage using JaCoCo"*
- *"I ran JUnits in AWS CloudShell as there were issues running Maven in local due to Zscaler"*

### Frontend — Jest
- *"Can you add unit test scripts for key UI components — what about using Jest?"*

---

## Phase 6 — Security & Code Quality

- *"I want to run some kind of vulnerability scan and code review scan — does Sonar cover both? Do you have any other suggestions?"*
- *"I can't use Docker — can I still run a Sonar scan on my code with just Maven?"*
- *"What about OWASP scan — can I run it in Maven?"*
- *"Are there any options available within AWS cloud for both Sonar and OWASP kind of scans?"*
- *"What if I link CodeGuru to my Git repo and run code reviewer — does it cover?"*
- *"Getting this error when I try to associate CodeGuru — AssociateRepository API is no longer supported"* → pivoted to alternatives
- *"Instead of all these things, can I use you (Claude Code) to do the SAST?"*
- *"/security-review scan my repo and share the report"*
- *"First create a report of your findings in HTML format in the workspace. Fix all 3 critical issues. Ensure no impact to any functionality."*
- *"Remove Sonar and OWASP dependencies from pom.xml as we don't need them anymore"* (moved to buildspec.yml)
- *"I used the security-review skill in Claude Code and also enabled all security scans in GitHub — can you consolidate what and all it covers from security and code quality perspective?"*

---

## Phase 7 — UI Fixes & Polish

- *"In the OTP page, back link is not working"*
- *"When I register a new email ID, I am not getting OTP"*
- *"What is the significance of the Actions column in the User tab — if it's always going to be empty, let's remove it; else fix if there is missing functionality"*
- *"In admin login, under users tab, status column still shows as inactive"*
- *"There is some extra white layer below every text box. In admin login, currency pair display under 'All Transactions' tab and 'Rates' tab are different — keep them in sync, follow what's in 'All Transactions' tab like USD → INR instead of USD/INR"*
- *"Deployed recent build and it's taking to MFA page for admin user — for admin user it shouldn't ask OTP"*

---

## Phase 8 — Architecture Review & Documentation

- *"Review api-contract.md file and update it if needed as per the code"*
- *"What skills can we create for this project — as I am going to submit this project as an assignment submission for my architect program"*
- *"/arch-check → Save this run report in HTML"*
- *"We have made a lot of changes in code — ensure CLAUDE.md is up to date"*
- *"Can you consolidate key prompts I used in building this project? I want to document it in my final submission."*

---

## Summary: AI-Assisted Development Coverage

| Phase | Claude Code Contribution |
|---|---|
| Architecture to code | Generated all backend layers (entities, repos, services, controllers) from `/docs` specs |
| Auth & Security | JWT RS256 + MFA OTP flow, Spring Security filter chain, RBAC |
| Frontend | Full Angular 18 SPA scaffolded from API contract and functional requirements |
| DB Migrations | Flyway SQL scripts generated from data model doc |
| Feature addition | Configurable approval threshold — DB-backed config, admin API, frontend integration |
| Testing | JUnit 5 test suites (136 tests), JaCoCo coverage (94% line), Jest frontend setup |
| Security scanning | `/security-review` SAST, OWASP Dependency-Check, SonarCloud integration via buildspec.yml |
| Deployment debug | Diagnosed 502/504 errors, JWT cycle dependency, MFA bypass for POC, cookie.secure config |
| Documentation | CLAUDE.md, README, API contract updates, arch-check HTML report, this prompt library |
