# UC-511 – Athlete Running Training History

## Purpose and access

Return the running segment history for one athlete without exposing other participants.
ADMIN may access any athlete. COACH may access athletes in their currently assigned groups.
ATHLETE may access only their linked athlete. An inaccessible athlete returns the same
not-found response as a missing athlete. History includes the athlete's own historical
participations even if their training group has since changed.

The ability to open full training details is separate: ADMIN may open any training,
COACH may open only training sessions whose group is currently assigned to them, and
ATHLETE cannot open training details. UC-504 remains authoritative.

## API

`GET /api/athletes/{athleteId}/training-history` accepts optional ISO calendar
dates `fromDate` and `toDate`, both inclusive. A reversed range or invalid ID/date
is rejected. A valid query without matching sessions returns an empty list.

Each item contains `trainingId`, `trainingDate`, `canViewDetails`, `totalSegments`,
and `segments`. Each segment contains `segmentId`, `sequenceNumber`, `distanceM`,
`status`, `measuredResult`, and `usesSpikes`. Times are seconds, with up to three
decimal places; zero is valid. Spikes are independent of execution status.
`canViewDetails` is a point-in-time navigation hint, not an authorization grant.

Include a session if at least one of the athlete's executions is not `PENDING`.
Include all segments of the athlete's assigned definition, including pending and
non-result statuses. The total is the full definition count; sequence numbers are
the persisted numbers, not indexes in a filtered collection. Order sessions by
date descending and ID descending, and segments by sequence number and ID.
Date filtering is performed in the database; no distance filtering or aggregation
is performed by the backend.

## Dependencies and constraints

UC-002 provides athlete access scope; UC-502, UC-503 and UC-510 define the
training structure and executions; UC-504 governs full training details.
The history reflects the current persisted structure, not an immutable snapshot.
No new schema or entity is required. Missing execution rows violate the training
invariant and must not be silently presented as pending.
