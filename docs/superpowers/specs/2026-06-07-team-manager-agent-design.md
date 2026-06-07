# Team Manager Agent Design

## Goal

Add a full pre-match workbench to the existing calculator page. The workbench helps a logged-in team member simulate match outcomes, compare loan options, and ask an interactive "team manager" agent for advice.

The first implementation targets decision support only. It does not create matches, publish match results, mutate team balances, or write official ledgers.

## User Experience

The `tools.html` page will evolve from two simple calculators into a three-column pre-match workbench:

1. Match input
   - Current user's team is resolved from the login cookie and `/api/user/info`.
   - Opponent is required.
   - Format is required: `BO2` or `BO3`.
   - My starting lineup is required, with partial lineups allowed for loan planning.
   - Opponent loans are optional context.
   - Strategy preference can be `冲一冲`, `稳一稳`, or `保一保`.

2. Simulation results
   - Show every relevant score scenario for the selected format.
   - For `BO2`: `2:0`, `1:1`, `0:2`.
   - For `BO3`: `2:0`, `2:1`, `1:2`, `0:2`.
   - For each scenario, show match reward, luxury tax, loan fees, net team P change, projected balance, and warnings.
   - If my lineup has fewer than 5 players, show loan recommendations for the selected strategy and the resulting simulated economics.

3. Interactive manager chat
   - The right panel contains a message timeline, quick prompts, a text input, and send/loading/error states.
   - Quick prompts include examples like `最省钱`, `胜率优先`, `避免爆税`, and `解释推荐`.
   - Each chat request includes the active workbench state, the latest simulation result, and conversation history.
   - The agent answers with advice and explanations only. It does not perform official writes.

## Backend Architecture

Add a new public authenticated AI module under `/api/ai/team-manager`.

Endpoints:

- `GET /api/ai/team-manager/context`
  - Requires login.
  - Returns the current user's team, all teams, active players, rule parameters, active reward rules, and a compact rule knowledge summary.

- `POST /api/ai/team-manager/simulate`
  - Requires login.
  - Accepts opponent, format, my lineup player IDs, optional opponent loan context, optional manual loan candidates, and strategy preference.
  - Returns deterministic simulation rows for each score scenario.
  - Returns loan recommendations when my lineup has fewer than 5 players.

- `POST /api/ai/team-manager/chat`
  - Requires login.
  - Accepts the current workbench input, latest simulation result, and chat messages.
  - Rebuilds trusted backend context before calling DeepSeek.
  - Calls DeepSeek through a server-side API key and returns the assistant reply.

## DeepSeek Integration

Use server-side configuration only:

- `ltl.ai.deepseek.api-key` from `DEEPSEEK_API_KEY`.
- `ltl.ai.deepseek.base-url`, default `https://api.deepseek.com`.
- `ltl.ai.deepseek.model`, default `deepseek-v4-flash`.
- Optional timeout and token limit properties.

The browser never receives the API key. The key must not be written into source code, YAML, docs, logs, or tests.

The chat service uses the OpenAI-compatible chat completions API shape:

- `POST /chat/completions`
- Bearer token authentication.
- System message describes the agent as an LTL team manager.
- Context message contains rules, teams, players, current team, active simulation, and strict instructions to cite deterministic simulation numbers instead of inventing calculations.

## Deterministic Simulation

Numeric calculations stay in Java code, not in the LLM.

The simulator should reuse or extract logic from existing settlement services where practical:

- Reward rules come from `settlement_reward_rules`.
- Luxury tax uses the same formula as `MatchSettlementCalculator`.
- Loan fee uses the same formula as `MatchSettlementCalculator`.
- League standard salary line should match the current settlement calculation using active players.

Simulation output for a team includes:

- `scorePattern`
- `matchReward`
- `luxuryTax`
- `loanFeePaid`
- `loanFeeReceived`
- `netPChange`
- `balanceBefore`
- `balanceAfter`
- `warnings`
- calculation breakdown rows suitable for UI display and AI context.

The initial version does not estimate true match win probability. Strategy labels are economic heuristics:

- `冲一冲`: allow stronger or higher-value loan candidates when positive upside exists.
- `稳一稳`: prefer balanced candidates that avoid large tax spikes.
- `保一保`: prefer lowest cost candidates that complete the lineup and protect balance.

## Loan Recommendation Rules

When my selected lineup has fewer than 5 players:

1. Build eligible loan candidates from active players not on my team and not already selected.
2. Exclude the opponent team's players by default to avoid conflict, unless explicitly allowed later.
3. Use current player value as the default settlement value.
4. Treat players with no team as free agents; otherwise source type is original team.
5. For each candidate, simulate the selected format and score scenarios after adding the player.
6. Rank according to strategy:
   - `冲一冲`: higher lineup value and best upside scenario, while warning about tax and balance risk.
   - `稳一稳`: best median or draw/close-result net outcome with manageable tax.
   - `保一保`: lowest total cost and lowest negative-balance risk.

The first version recommends single-player loans only. Multi-player optimization can be added after the first workbench is stable.

## Frontend Architecture

Add a dedicated module for the workbench rather than expanding `calculators.js` indefinitely.

Expected modules:

- `src/features/teamManagerAgent.js`
  - Renders and manages the workbench.
  - Tracks input state, simulation result, chat history, loading states, and errors.
  - Calls API service functions.

- `src/services/api.js`
  - Add `getTeamManagerContext`, `simulateTeamManagerPlan`, and `chatWithTeamManager`.

`tools.html` will contain a new workbench container while preserving the existing simple calculators as a secondary section.

The UI should remain plain JavaScript and existing CSS. No new frontend framework is introduced.

## Error Handling

- If not logged in, show a login-required state for the workbench.
- If DeepSeek is not configured, simulation remains available and chat shows a clear configuration message.
- If DeepSeek call fails, preserve chat history and show retry affordance.
- If required fields are missing, disable simulation/chat submit and show inline validation.
- If a projected team balance goes negative, show warnings but still return the simulation.

## Testing

Backend tests:

- Simulation enumerates the correct score patterns for `BO2` and `BO3`.
- Simulation applies reward, luxury tax, and loan fee calculations consistently.
- Loan recommendations appear only when the lineup is under 5 players.
- Chat service rejects missing API key cleanly and does not log secrets.

Frontend checks:

- Existing `npm run check` continues to pass.
- Workbench module parses as valid JavaScript.
- Manual browser verification covers logged-out state, required field validation, simulation display, and chat error/loading states.

## Open Follow-Ups

- Whether team managers should be allowed to include opponent players as emergency loan candidates.
- Whether recommendations should eventually use player position matching instead of pure economic strategy.
- Whether chat transcripts should be persisted. The initial version keeps them in page memory only.
