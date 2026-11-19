# FE-TRAINING-011 – Export and Import Trainings

## 1. Purpose

Provide ADMIN and COACH users with JSON training transfer actions on the training list, following UC-513 and UC-514.

## 2. Export

- Add **Eksportuj do JSON** to the existing selected-items **Akcje** menu. Reuse its selection, including selections hidden by current filters.
- Submit all selected IDs through the trainings API service. Download the returned JSON attachment as one file.
- Disable duplicate requests while export is in progress. Show Polish error feedback and retain selection after failure.
- Do not show the export action to ATHLETE. Backend authorization remains authoritative.

## 3. Import

- Add **Importuj z pliku JSON** as a separate training-list action, distinct from the selected-items menu and the existing photo import.
- Permit one `.json` file up to 1 MiB. Client checks provide usability only; the backend performs authoritative parsing and complete-file validation.
- Do not open the training assignment dialog until full validation succeeds. Present backend validation errors in Polish and do not display a success state.
- Display the full content of each validated training and a checkbox, checked by default. Common group and optional date defaults appear when multiple trainings are present and apply to every row. Each row can override group and date independently; values remain intact when checked state changes.
- Keep date empty unless supplied as a dialog default or individual choice; never use the date from the exported content.
- TEMPO rows require a group. FREE_TEXT rows default to GROUP and allow switching to ATHLETES, which requires one or more active athlete selections and no group. Use current group and athlete selectors.
- A row can be deselected without discarding its content or defaults. Only checked rows are created. Disable Import when none are checked.
- Retain the selected source file through the dialog. Submit it with all rows' selection/date/assignment metadata so the backend can revalidate the exact complete file; only selected entries are created. On success, close the dialog, show a Polish success message and refresh the training list.
- Keep the dialog accessible, scrollable for large files, responsive, and consistent with existing form, dialog, and purple primary-button styling.

## 4. Authorization and Errors

- Show JSON export/import actions only to ADMIN and COACH users. Never rely on client-side role checks for security.
- Handle authentication, forbidden access, validation, size-limit, network and transactional errors without losing dialog selections or implying partial success.

## 5. Tests

- Verify action visibility, selected-ID export/download, and that JSON import is separate from bulk actions and photo import.
- Verify validation must succeed before the dialog opens; show localized errors on invalid input.
- Verify common defaults, independent row overrides, assignment-mode behavior, persistence of values during checkbox toggles, all-selected default, selected-only submission and disabled empty selection.
- Verify successful import closes the dialog and refreshes the list; failures retain entered state.
