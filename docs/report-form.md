# Setting up the report form (5 minutes, free)

Google Play requires apps with AI-generated content to let users report offensive replies from
inside the app. AI Launcher sends each report straight to a Google Form that you own, so there is
no server to run and reports appear in your Google account.

1. Go to forms.google.com and create a blank form, for example "AI Launcher reports".
2. Add these questions, each as **Paragraph**, in any order:
   - Reason
   - Details
   - Question
   - Reply
   - App
3. Optional: *Responses ▸ Link to Sheets* to see reports in a spreadsheet.
4. Open the ⋮ menu ▸ **Get pre-filled link**. Type exactly these words into the matching
   questions: `REASON`, `DETAILS`, `QUESTION`, `REPLY` and `APP`. Click **Get link** and **Copy
   link**.
5. Put the link into `local.properties`:

   ```properties
   report.form.url=https://docs.google.com/forms/d/e/…/viewform?usp=pp_url&entry.…=REASON&…
   ```

6. Rebuild the Play version. Reports are now posted to the form in the background (and retried
   when the phone is offline).

Only the `REPLY` field is required; leave out any question you don't want. Use the full
`docs.google.com/forms/…` link, not a shortened `forms.gle` one. If you ever delete the form, set up
a new one and rebuild, otherwise reports will fail.
