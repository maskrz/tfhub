# UC-507 – Process Training Import

## 1. Purpose

Provide an asynchronous process for recognizing and extracting training data from an uploaded training sheet photo.

The process must use an AI-based image recognition service to identify the training structure, recognize handwritten execution data, match athletes against the selected training group and create an editable import draft.

The process must not create or modify persisted training sessions or athlete execution records.

## 2. Actors

- **System** – processes pending training imports asynchronously.
- **ADMIN** – can retry processing for imports they are authorized to access.
- **COACH** – can retry processing for imports belonging to groups currently assigned to that coach.

## 3. Preconditions

- A training import has been successfully created through UC-506.
- The import has an associated optimized JPEG source image.
- The import has an associated training group.
- The import is in a state eligible for processing or retry.
- The requesting user, if retrying processing, is authorized to access the import.

## 4. Business Rules

### 4.1 Asynchronous Processing

- Image processing must be performed asynchronously.
- The upload request from UC-506 must not wait for OCR processing to finish.
- Each import must be processed independently.
- The processing operation must not create duplicate processing tasks for the same import.
- The system must prevent concurrent processing of the same import.
- Processing must be associated with the original import identifier.
- The system must track processing status and relevant timestamps.
- Each attempt must retain the exact raw model response when the model returns one, including responses that fail validation. This troubleshooting data must not be exposed in API responses or application logs.
- Processing failures must be recorded without exposing internal implementation details or API credentials.

### 4.2 Import Processing States

The system must distinguish at least the following import states:

- **PENDING** – import is waiting to be processed.
- **PROCESSING** – image recognition and extraction are in progress.
- **REVIEW_REQUIRED** – processing completed and a draft is available for review.
- **FAILED** – processing failed and can be retried.
- **REJECTED** – import was explicitly rejected by an authorized user.
- **ACCEPTED** – import was successfully accepted and converted into a persisted training session.

The exact state transition rules must be enforced by the backend.

### 4.3 Image Recognition

- The optimized JPEG retained by UC-506 must be used as the source document.
- The initial implementation must use Gemini Flash-Lite 3.5 for image understanding and handwriting recognition.
- Exactly one Gemini model call is made for each processing attempt; the system does not call a fallback or second model.
- The model must receive the image together with instructions defining the expected training sheet structure and recognition rules.
- The model must identify the training name and date.
- The model must identify training definitions and their names.
- The model must identify the ordered segments within each definition.
- The model must recognize segment distances and rest periods where present.
- The model must identify athlete rows and their handwritten result cells.
- The model must recognize measured values, completion markers and spike indicators.
- The model must return structured data suitable for backend validation and further processing.
- The model response must not be treated as trusted application data.

### 4.4 Handwritten Execution Data Recognition

The system must recognize the following execution states:

- A confidently recognized measured value represents **RECORDED**.
- A standalone `X` represents **COMPLETED_WITHOUT_RESULT**.
- A standalone dash represents **NOT_COMPLETED**.
- An empty result cell represents **PENDING**.
- A crossed-out value whose final result cannot be reliably determined must be marked for manual review rather than interpreted as a valid measured result.

Additional recognition rules:

- Running times must be normalized to the agreed result notation.
- Missing fractional digits must be normalized without automatically marking the value as uncertain.
- A single fractional digit must be normalized to two fractional digits.
- Spike usage must be recognized independently for each segment execution.
- A handwritten `K` or the designated graphical spike marker represents spike usage.
- A graphical spike marker must not be interpreted as part of the measured result.
- Uncertain recognition must be explicitly flagged for manual review.
- The system must preserve the original recognized text for comparison with normalized values.
- The model must not invent values for empty or unreadable cells.

### 4.5 Training Group Context

- The training group selected during UC-506 is authoritative for athlete matching.
- The import must remain associated with the same training group throughout processing.
- Athlete matching must be restricted to athletes belonging to the selected group at the time of processing.
- The system must not search for matching athletes in other groups.
- The system must not automatically change the import's training group based on text recognized in the image.
- A group name recognized in the image may be retained as informational data for review but must not override the selected group.

### 4.6 Athlete Matching

- Recognized athlete names must be matched against existing athletes in the selected training group.
- Matching must use available identifying information, including first name and last name.
- The system must not assume that a name match is unambiguous if multiple candidates exist.
- A confidently matched athlete may be associated with the corresponding draft participant.
- An ambiguous or unmatched athlete must remain unresolved.
- An athlete who cannot be matched must not be assigned to another group automatically.
- Unresolved athletes must be explicitly identified in the draft.
- Every unresolved athlete must be matched to an existing athlete from the selected group or explicitly skipped before the import can be accepted.
- The system must not create new athlete records as part of OCR processing.

### 4.7 Draft Creation

- Successful processing must produce a structured import draft.
- The draft must contain recognized training information, definitions, segments, participants and execution data.
- The draft must preserve the association between each athlete, definition, segment and recognized execution.
- The draft must include recognition uncertainty and unresolved athlete matching information.
- The draft must retain original recognized text where applicable.
- The draft must be editable through UC-508.
- Processing must not create persisted training session, definition, segment, participation or execution records.
- The optimized JPEG must remain available for subsequent review.

### 4.8 Processing Retry

- An import in FAILED state may be retried using the same optimized JPEG.
- Retry must replace the current draft only after the new attempt succeeds and passes basic output validation.
- A failed retry must leave any existing valid draft unchanged.
- Retry must not create a second import record.
- Retry must not create duplicate training session or execution records.
- A retry must start a new processing attempt associated with the same import identifier.
- The system must retain sufficient processing history to diagnose repeated failures.
- Retry must be rejected while the import is already PROCESSING.
- An import in REVIEW_REQUIRED, ACCEPTED or REJECTED state cannot be retried through this operation.

### 4.9 AI Service Failure

- Temporary AI service errors must result in a failed processing attempt.
- Invalid, incomplete or unusable model responses must not produce an accepted draft.
- The system must validate the model response before creating or replacing the draft.
- Failure must leave the optimized JPEG intact.
- The import must enter FAILED state if processing cannot be completed.
- Failure information must be available for diagnosis without exposing API keys, credentials or sensitive configuration.
- The system must not silently accept partially recognized data as a completed import.

## 5. Authorization Rules

- The system may process pending imports asynchronously without requiring an active user request.
- Only ADMIN and COACH users may explicitly retry failed processing.
- ADMIN can access imports belonging to any training group.
- COACH can access and retry imports only while currently assigned to the import's group.
- A coach who is no longer assigned to the import's group cannot retry its processing.
- The backend must derive the requesting user's identity and permissions from the authenticated security context.
- The backend must not rely on client-supplied user identifiers, roles or group assignments.

## 6. Validation and Error Scenarios

- **Import does not exist:** the operation returns an appropriate not-found response.
- **COACH is not currently assigned to the import's group:** the operation returns the same not-found response as for a nonexistent import.
- **Import is not in a state eligible for processing:** processing is rejected.
- **Import is already PROCESSING:** another processing attempt is rejected.
- **Source image is missing or unreadable:** processing fails.
- **AI service is unavailable:** processing fails and can be retried.
- **AI service returns an invalid response:** processing fails.
- **Recognized data cannot be validated:** processing fails rather than producing an invalid draft.
- **Athlete cannot be matched:** the draft is created with unresolved athlete information requiring review.
- **Recognition is uncertain:** the draft is created with the relevant fields flagged for review.
- **Unexpected server error:** the system reports an appropriate error without exposing internal implementation details.

A failed processing attempt must not create or modify persisted training session data.

## 7. Output

The asynchronous process produces an updated training import containing its current processing state. Clients retrieve the current state from `GET /api/training-imports/{importId}`. An authorized user retries a failed import using `POST /api/training-imports/{importId}/retry`, which returns `202 Accepted`.

When processing succeeds, the import must expose sufficient information to retrieve and review the generated draft through UC-508.

## 8. Transactional Consistency

- Processing state transitions must be persisted consistently.
- A draft must become available only after its structure has passed backend validation.
- Replacing a draft during retry must be atomic.
- A failed retry must not leave a partially replaced draft.
- The optimized JPEG must remain intact throughout processing and retry.
- Processing must never create persisted training session or execution records.
- Concurrent processing attempts for the same import must be prevented.

## 9. Out of Scope

- Uploading training photos.
- Editing recognized draft data.
- Manually matching or skipping unresolved athletes.
- Accepting or rejecting imports.
- Creating persisted training sessions.
- Persisting athlete execution results.
- Training statistics and athlete training history.
- Training templates and recurring training plans.
- Automatic acceptance based on model confidence.
- Running multiple AI models or consensus-based recognition.
- Automatic creation of new athlete records.

## 10. Acceptance Criteria

1. Training imports created through UC-506 can be processed asynchronously.
2. Uploading a photo does not wait for image recognition to finish.
3. The initial implementation uses Gemini Flash-Lite 3.5.
4. The system recognizes training name and date.
5. The system recognizes training definitions, segments, distances and rest periods.
6. The system recognizes athlete rows and execution data.
7. The system distinguishes RECORDED, COMPLETED_WITHOUT_RESULT, NOT_COMPLETED and PENDING states.
8. Running times are normalized according to the agreed notation.
9. Spike usage is recognized independently for each segment execution.
10. Uncertain recognition is explicitly flagged for review.
11. Athlete matching is restricted to the group selected during upload.
12. Unmatched or ambiguous athletes remain unresolved and must be explicitly matched or skipped before acceptance.
13. The system does not automatically create athlete records.
14. Successful processing creates a structured editable draft.
15. Successful processing changes the import state to REVIEW_REQUIRED.
16. Failed processing changes the import state to FAILED.
17. A failed import can be retried using the same optimized JPEG.
18. Retry replaces the current draft only after successful processing and validation, without creating a new import record.
19. Concurrent processing attempts for the same import are prevented.
20. The optimized JPEG remains available after processing and retry; the unoptimized upload is not retained.
21. Processing never creates or modifies persisted training session or execution records.
22. AI service failures and invalid responses do not produce an accepted draft.
23. Processing status and failure information are persisted.
24. The raw model response is retained per attempt when returned and is not exposed to clients or logs.
25. Unauthorized users cannot retry processing.