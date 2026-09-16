July 2026 Att.log workbook builder
==================================

JulyAttLogBuilder.java builds a biometric-style "Att.log report" Excel file for
July 2026 from best-effort OCR / visual transcription of paper Att.log photos.

Output (when run):
  C:\Users\akkat\Downloads\Attendance_July_2026_AttLog.xlsx

Important:
- This is best-effort OCR from photos, not a machine export.
- Blank day cells mean unread or empty on the paper.
- Spot-check multipunch days and leave ("L") / half-day (" HD") / PRESENT cells
  against the original photos before payroll use.
- Days that were not explicitly readable were left blank on purpose (no invented
  full-month schedules).
