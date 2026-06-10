# P哥推荐 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an admin-only P哥推荐 workbench below the calculators with deterministic team economics simulation and an interactive DeepSeek-backed chat.

**Architecture:** Backend exposes `/api/ai/team-manager` endpoints and keeps deterministic math in Java. Frontend renders a three-column workbench on `tools.html`, calls the backend for context/simulation/chat, and never sees the DeepSeek key.

**Tech Stack:** Spring Boot 2.7, Java 17, MyBatis-Plus, plain ES modules, existing CSS, DeepSeek OpenAI-compatible chat completions API.

---

## File Structure

- Create `backend/src/main/java/com/ltl/league/ai/dto/TeamManagerDtos.java`: request/response DTOs for context, simulation, recommendations, and chat.
- Create `backend/src/main/java/com/ltl/league/ai/service/TeamManagerSimulationService.java`: deterministic simulation and recommendation logic.
- Create `backend/src/main/java/com/ltl/league/ai/service/DeepSeekTeamManagerChatService.java`: server-side DeepSeek chat completion wrapper.
- Create `backend/src/main/java/com/ltl/league/ai/controller/TeamManagerAgentController.java`: authenticated REST endpoints.
- Create `backend/src/test/java/com/ltl/league/ai/service/TeamManagerSimulationServiceTest.java`: red/green tests for score enumeration, economics, and recommendation behavior.
- Modify `backend/src/main/resources/application.yml`: non-secret DeepSeek defaults only.
- Modify `src/services/api.js`: add P哥推荐 API functions.
- Create `src/features/teamManagerAgent.js`: UI state, render, validation, simulation calls, chat calls.
- Modify `src/main.js`: initialize the workbench.
- Modify `tools.html`: add the workbench container.
- Modify `src/styles/main.css`: workbench layout and chat styles.
- Modify `package.json`: include the new JS file in `npm run check`.

## Tasks

### Task 1: Backend Simulation RED

- [ ] Create `TeamManagerSimulationServiceTest` with tests that require:
  - `BO2` score patterns are `2:0`, `1:1`, `0:2`.
  - A `2:0` scenario combines reward, luxury tax, and loan fee into net P change.
  - Under-5 lineup returns loan recommendations.
- [ ] Run `cd backend && mvn -Dtest=TeamManagerSimulationServiceTest test`.
- [ ] Expected result: compile failure because `TeamManagerSimulationService` and DTOs do not exist.

### Task 2: Backend Simulation GREEN

- [ ] Add `TeamManagerDtos` with nested DTO classes used by controller, service, and tests.
- [ ] Add `TeamManagerSimulationService`.
- [ ] Use existing `MatchSettlementCalculator` for luxury tax and loan fee.
- [ ] Implement score patterns for `BO2` and `BO3`.
- [ ] Implement current-team-perspective reward lookup from `SettlementRewardRule`.
- [ ] Implement single-player loan recommendations for under-5 lineups.
- [ ] Run `cd backend && mvn -Dtest=TeamManagerSimulationServiceTest test`.
- [ ] Expected result: test passes.

### Task 3: Backend API and Chat

- [ ] Add `DeepSeekTeamManagerChatService`.
- [ ] Add `TeamManagerAgentController` with:
  - `GET /ai/team-manager/context`
  - `POST /ai/team-manager/simulate`
  - `POST /ai/team-manager/chat`
- [ ] Controller must parse `ltl_auth`, reject missing/expired login, and use `UserService.getUserInfo`.
- [ ] Load teams, players, rules, rule parameters, and active reward rules from existing mappers/services.
- [ ] Configure DeepSeek from environment-backed Spring properties, not source code secrets.
- [ ] Run `cd backend && mvn test`.
- [ ] Expected result: backend tests pass.

### Task 4: Frontend Workbench

- [ ] Add API helpers in `src/services/api.js`.
- [ ] Create `src/features/teamManagerAgent.js` with:
  - logged-out and logged-in render states
  - opponent/format/lineup/strategy inputs
  - deterministic simulation result display
  - loan recommendations display
  - interactive message timeline
  - quick prompts and free-text chat
- [ ] Update `tools.html` and `src/main.js`.
- [ ] Add CSS in `src/styles/main.css`.
- [ ] Update `package.json` check script.
- [ ] Run `npm run check`.
- [ ] Expected result: JS syntax check passes.

### Task 5: End-to-End Verification

- [ ] Run `cd backend && mvn test`.
- [ ] Run `npm run check`.
- [ ] Start or reuse local backend/frontend if available.
- [ ] Verify in browser:
  - logged-out workbench asks for login
  - logged-in context loads
  - missing required fields block simulation
  - simulation results render after valid inputs
  - chat shows clear configuration error if `DEEPSEEK_API_KEY` is absent
- [ ] Review diff for accidental API key leakage.
