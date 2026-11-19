# UC-513 – Export Trainings

## 1. Purpose

Export selected persisted trainings as one portable, versioned JSON document. The document contains reusable TEMPO or FREE_TEXT definitions only, not a training event's execution history or environment-specific assignments.

## 2. Actors and Preconditions

- **ADMIN** – may export any persisted training.
- **COACH** – may export trainings they can currently manage under UC-501/UC-503.
- The requester is authenticated and has the ADMIN or COACH role.
- The selected list contains positive, unique training IDs and is non-empty.

## 3. JSON Format Version 1

The UTF-8 JSON document has this shape:

```json
{
  "formatVersion": 1,
  "trainings": [
    {
      "name": "Interwały",
      "trainingType": "TEMPO",
      "definitions": [
        {
          "name": "Wariant A",
          "segments": [
            {"distanceM": 400, "restAfterSeconds": 60},
            {"distanceM": 200, "restAfterSeconds": null}
          ]
        }
      ]
    },
    {
      "name": "Mobilność",
      "trainingType": "FREE_TEXT",
      "sections": [
        {"title": "Rozgrzewka", "content": "Trucht i ćwiczenia mobilizacyjne"}
      ],
      "equipment": "Gumy oporowe"
    }
  ]
}
```

- Object and array order in `trainings`, `definitions`, `segments` and `sections` is authoritative. Import assigns new sequence numbers from array positions.
- A TEMPO training has a non-blank name and one or more definitions; each definition has a non-blank name and one or more positive-distance segments. Rest is null or a non-negative integer.
- A FREE_TEXT training has a non-blank name and one or more sections with non-blank titles and content. `equipment` is optional and may be null or text.
- Version 1 rejects unknown or duplicate properties, malformed JSON, trailing JSON values, unsupported training types, wrong field types and invalid structures. Dates, status, IDs, assignments, athlete data, participation, execution, results and OCR metadata are not permitted.
- The writer emits version 1. Any future incompatible schema change increments `formatVersion`; readers reject unsupported versions rather than guessing or silently discarding data.

## 4. API Contract and Main Flow

1. The client sends the selected IDs to `POST /api/trainings/export` as `{"trainingIds":[...]}`.
2. The backend retrieves and authorizes every selected training, then returns one UTF-8 JSON attachment using the format above.

The export contains the training name, type-specific definition and ordering, distances/rest periods or complete FREE_TEXT sections, and FREE_TEXT equipment. It omits `trainingDate`, status, all database identifiers, group and individual assignments, athlete identities, participations, executions, measured results, completion history and import/OCR metadata.

An inaccessible or missing selected training fails the whole operation; the response must not contain partial or broadened data. Export is read-only and creates no database records.

## 5. Authorization and Errors

- Only ADMIN and COACH may export. ATHLETE access is denied even when the athlete can view a FREE_TEXT training.
- ADMIN may export any training. COACH must currently be authorized for each selected training, including the existing all-selected-athletes scope check for ATHLETES-mode FREE_TEXT.
- Access is derived from the authenticated security context, never client-supplied role or identity.
- Invalid ID lists and missing or inaccessible selections fail the request as a whole with the standard API error response.

## 6. Out of Scope

- Exporting participation, execution, result, athlete or import/OCR data.
- Exporting or restoring assignment, date, status, IDs or execution history.
- Selecting trainings by filter instead of explicit IDs.
- Importing JSON; see UC-514.
