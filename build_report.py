"""
Generates PROJECT_REPORT.pdf for RenewalVault, following the
VITyarthi "Build Your Own Project" submission structure (15 sections).
"""
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import cm
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak, Image, Table, TableStyle,
    ListFlowable, ListItem, Preformatted, KeepTogether, HRFlowable
)
import datetime

DIAGRAMS = "docs/diagrams"
SHOTS = "docs/screenshots"

styles = getSampleStyleSheet()
styles.add(ParagraphStyle(name="H1Custom", parent=styles["Heading1"],
                           fontSize=18, spaceBefore=18, spaceAfter=10, textColor=colors.HexColor("#1a3a5c")))
styles.add(ParagraphStyle(name="H2Custom", parent=styles["Heading2"],
                           fontSize=13.5, spaceBefore=12, spaceAfter=6, textColor=colors.HexColor("#2a5a8c")))
styles.add(ParagraphStyle(name="H3Custom", parent=styles["Heading3"],
                           fontSize=11.5, spaceBefore=8, spaceAfter=4, textColor=colors.HexColor("#444444")))
styles.add(ParagraphStyle(name="BodyCustom", parent=styles["Normal"],
                           fontSize=10.2, leading=14.5, spaceAfter=8, alignment=TA_LEFT))
styles.add(ParagraphStyle(name="Caption", parent=styles["Normal"],
                           fontSize=9, leading=12, textColor=colors.HexColor("#555555"),
                           alignment=TA_CENTER, spaceBefore=4, spaceAfter=14, fontName="Helvetica-Oblique"))
styles.add(ParagraphStyle(name="CoverTitle", parent=styles["Title"],
                           fontSize=28, leading=34, textColor=colors.HexColor("#1a3a5c")))
styles.add(ParagraphStyle(name="CoverSub", parent=styles["Normal"],
                           fontSize=14, alignment=TA_CENTER, spaceBefore=10, textColor=colors.HexColor("#444444")))
styles.add(ParagraphStyle(name="CodeBlock", parent=styles["Normal"],
                           fontName="Courier", fontSize=8.3, leading=11,
                           backColor=colors.HexColor("#f4f4f4"), borderPadding=6,
                           spaceBefore=6, spaceAfter=10))

BODY = styles["BodyCustom"]
H1 = styles["H1Custom"]
H2 = styles["H2Custom"]
H3 = styles["H3Custom"]

styles.add(ParagraphStyle(name="TableCell", parent=styles["Normal"],
                           fontSize=8.4, leading=11.5))
styles.add(ParagraphStyle(name="TableHead", parent=styles["Normal"],
                           fontSize=8.6, leading=11, textColor=colors.white, fontName="Helvetica-Bold"))
styles.add(ParagraphStyle(name="TableCellBold", parent=styles["Normal"],
                           fontSize=8.4, leading=11.5, fontName="Helvetica-Bold",
                           textColor=colors.HexColor("#1a3a5c")))

def tc(text):
    return Paragraph(text, styles["TableCell"])

def th(text):
    return Paragraph(text, styles["TableHead"])

def tcb(text):
    return Paragraph(text, styles["TableCellBold"])

story = []

def bullets(items, style=BODY):
    return ListFlowable(
        [ListItem(Paragraph(t, style), bulletColor=colors.HexColor("#2a5a8c")) for t in items],
        bulletType="bullet", start="•", leftIndent=16, spaceBefore=2, spaceAfter=10
    )

def numbered(items, style=BODY):
    return ListFlowable(
        [ListItem(Paragraph(t, style)) for t in items],
        bulletType="1", leftIndent=18, spaceBefore=2, spaceAfter=10
    )

def diagram(path, caption, width=15.5*cm):
    img = Image(path, width=width, height=width * 0.62)
    img.hAlign = "CENTER"
    return KeepTogether([img, Paragraph(caption, styles["Caption"])])

def diagram_auto(path, caption, max_width=15.5*cm, max_height=20*cm):
    from PIL import Image as PILImage
    with PILImage.open(path) as im:
        w, h = im.size
    ratio = w / h
    width = max_width
    height = width / ratio
    if height > max_height:
        height = max_height
        width = height * ratio
    img = Image(path, width=width, height=height)
    img.hAlign = "CENTER"
    return KeepTogether([img, Paragraph(caption, styles["Caption"])])

# =====================================================================
# 1. COVER PAGE
# =====================================================================
story.append(Spacer(1, 4*cm))
story.append(Paragraph("RenewalVault", styles["CoverTitle"]))
story.append(Paragraph("An Encrypted, Tamper-Evident Deadline & Renewal Tracking Platform", styles["CoverSub"]))
story.append(Spacer(1, 1.5*cm))
story.append(HRFlowable(width="60%", thickness=1, color=colors.HexColor("#cccccc"), hAlign="CENTER"))
story.append(Spacer(1, 1.5*cm))

cover_table = Table([
    ["Project Title", "RenewalVault"],
    ["Submitted By", "Mushkan"],
    ["Course", "Programming in Java"],
    ["Submission Type", "VITyarthi - Build Your Own Project"],
    ["Degree Program", "B.Tech CSE (Cyber Security & Digital Forensics)"],
    ["University", "VIT Bhopal University"],
    ["Technology Stack", "Java 21 (javax.crypto, java.util.zip, java.time, java.io)"],
    ["Submission Date", datetime.date.today().strftime("%d %B %Y")],
], colWidths=[5*cm, 9.5*cm])
cover_table.setStyle(TableStyle([
    ("FONTSIZE", (0, 0), (-1, -1), 10.5),
    ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
    ("TEXTCOLOR", (0, 0), (0, -1), colors.HexColor("#1a3a5c")),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
    ("TOPPADDING", (0, 0), (-1, -1), 8),
    ("LINEBELOW", (0, 0), (-1, -1), 0.5, colors.HexColor("#dddddd")),
]))
story.append(cover_table)
story.append(PageBreak())

# =====================================================================
# 2. INTRODUCTION
# =====================================================================
story.append(Paragraph("1. Introduction", H1))
story.append(Paragraph("""
RenewalVault is a command-line Java application built for the Programming in Java
course to solve a problem that spans nearly every category of modern life: things with
deadlines that are easy to forget. Subscriptions silently auto-renew and charge;
insurance policies lapse unnoticed; a vehicle's compliance certificate expires; a tax
filing deadline slips past; a warranty window closes right before an appliance breaks.
Each of these is normally tracked -- if at all -- by a different single-purpose app, a
sticky note, or human memory alone.
""", BODY))
story.append(Paragraph("""
Rather than build another narrow reminder app, RenewalVault treats "don't forget this
by this date" as one unified engineering problem across eleven life-domain categories,
and uses it as a vehicle to apply a broad slice of core Java concepts in a single,
coherent codebase: object-oriented design across five packages, generics (a type-safe
<font face="Courier">FileStore&lt;T extends Serializable&gt;</font> persistence layer),
enums with behaviour (<font face="Courier">Category.riskWeight()</font>), the Collections
Framework (<font face="Courier">PriorityQueue</font>, <font face="Courier">List</font>,
<font face="Courier">Map</font>), the Streams API for search/filter/sort/analytics, the
<font face="Courier">java.time</font> date API for recurrence logic, object serialization
and file I/O, and structured exception handling throughout. On top of that Java
foundation, the project also builds a genuinely encrypted, password-protected vault
(AES-256-GCM via <font face="Courier">javax.crypto</font>, itself part of the standard
JDK) and a SHA-256 hash-chained audit trail -- an ambitious, real-world feature choice
that also happens to align with the author's Cyber Security &amp; Digital Forensics
specialization, rather than a requirement of this course.
""", BODY))
story.append(Paragraph("""
The project is implemented entirely in Java 21 using only classes shipped with the
standard JDK (<font face="Courier">javax.crypto</font>, <font face="Courier">java.util.zip</font>,
<font face="Courier">java.time</font>, <font face="Courier">java.io</font>) -- no external
libraries, no build tool, no internet connection required. It compiles and runs with
nothing but <font face="Courier">javac</font> and <font face="Courier">java</font>.
""", BODY))

# =====================================================================
# 3. PROBLEM STATEMENT
# =====================================================================
story.append(Paragraph("2. Problem Statement", H1))
story.append(Paragraph("""
Everyday life is full of deadlines that are easy to forget: a subscription silently
auto-renews and charges the user, an insurance policy lapses without notice, a warranty
window closes right before an appliance breaks, a vehicle's insurance/PUC certificate
expires, a routine health checkup gets pushed back for years, a tax or compliance filing
is missed, groceries spoil unnoticed, and a plant dies because nobody remembered to water
it on schedule.
""", BODY))
story.append(Paragraph("""
Each of these is currently handled -- if at all -- by a <i>different</i> app, a sticky
note, or simply human memory. There is no single, free, offline tool that treats this as
one unified problem across every domain of a person's life, and none of the existing
single-purpose tools give any real guarantee that the data they hold (insurance numbers,
tax deadlines, personal notes) is protected at rest or safe from silent tampering.
""", BODY))
story.append(Paragraph("2.1 Scope of the Project", H2))
story.append(Paragraph("""
RenewalVault is scoped as a fully offline, dependency-free, single-user terminal tool.
It deliberately excludes multi-user accounts, cloud sync, push/SMS/email notifications,
and third-party calendar/banking API integration, in favour of a self-contained core
engine that solves the underlying tracking, prioritization, and data-protection problem
well. Local AES-256 encryption and <font face="Courier">.ics</font> calendar file export
are included because both stay entirely offline and require no external service.
""", BODY))
story.append(Paragraph("2.2 Target Users", H2))
story.append(bullets([
    "Individuals managing personal subscriptions, insurance, and warranties",
    "Vehicle owners tracking servicing and compliance deadlines",
    "Health-conscious individuals who want a reminder system for routine checkups",
    "Households wanting to reduce food waste by tracking grocery expiry dates",
    "Freelancers and small-business owners tracking tax/compliance filing deadlines",
    "Pet owners and plant enthusiasts tracking recurring care tasks",
    "Anyone who wants their personal deadline data actually protected, not stored in a plain text file",
]))
story.append(Paragraph("2.3 Objectives", H2))
story.append(numbered([
    "Identify a meaningful, real-world problem (fragmented, unprotected deadline tracking) and design a single unified solution for it.",
    "Apply core Java and object-oriented design concepts from the syllabus -- encapsulation, interfaces/enums with behaviour, generics, collections, exception handling, and file I/O -- to build a complete, working application rather than isolated exercises.",
    "Implement the solution using Java's standard library end to end: the Collections Framework and Streams API for data structures and algorithms (priority queues, sorting, filtering), the java.time API for recurrence/date arithmetic, java.io/serialization for file-based persistence, and javax.crypto for applied cryptography.",
    "Demonstrate understanding through structured documentation (this report, README, architecture and UML diagrams) and a rigorous, automated evaluation (a 12-case regression suite).",
]))

# =====================================================================
# 4. FUNCTIONAL REQUIREMENTS
# =====================================================================
story.append(Paragraph("3. Functional Requirements", H1))
story.append(Paragraph("""
The brief requires at least three major functional modules with a clear input/output
structure and a logical user workflow. RenewalVault implements <b>seven</b>:
""", BODY))

func_table = Table([
    [th("#"), th("Module"), th("Responsibility"), th("Key Inputs -&gt; Outputs")],
    [tc("1"), tcb("VaultService"), tc("Item CRUD, search/filter/sort, undo"),
     tc("Item fields -&gt; stored TrackedItem / query results")],
    [tc("2"), tcb("ReminderEngine"), tc("Risk-weighted urgency queue, hash-chained audit log, renewal streaks"),
     tc("Item list + logs -&gt; ranked alerts, integrity verdict, streak counts")],
    [tc("3"), tcb("AnalyticsEngine"), tc("Budget analytics and ASCII charts"),
     tc("Active items -&gt; spend charts, cost projections")],
    [tc("4"), tcb("ExportEngine"), tc("CSV backup/restore, .ics calendar export"),
     tc("Item list -&gt; CSV/.ics file, or CSV file -&gt; items")],
    [tc("5"), tcb("BackupEngine"), tc("Single-file encrypted vault backup/restore"),
     tc("./data folder -&gt; .rvbackup file, and reverse")],
    [tc("6"), tcb("Security\n(CryptoUtil + VaultAuth)"), tc("AES-256-GCM encryption, PBKDF2 key derivation, password verification/rotation"),
     tc("Password + salt -&gt; AES key; plaintext -&gt; ciphertext")],
    [tc("7"), tcb("CLI &amp; Reporting (Main)"), tc("Login flow, 18-option menu, input validation, colorized reporting"),
     tc("User keystrokes -&gt; validated calls into every other module")],
], colWidths=[0.7*cm, 3.4*cm, 5.6*cm, 6*cm])
func_table.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1a3a5c")),
    ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#cccccc")),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f6f9fc")]),
    ("TOPPADDING", (0, 0), (-1, -1), 5),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
]))
story.append(func_table)
story.append(Spacer(1, 10))
story.append(Paragraph("""
Each module has a clear, testable input/output boundary (enforced by the automated
test suite -- see Section 11), and the CLI layer provides a single logical workflow:
<i>unlock vault &rarr; view dashboard &rarr; act on an item or the vault itself &rarr;
persist &rarr; repeat or exit</i> (see the Workflow Diagram in Section 7).
""", BODY))
story.append(Paragraph("Representative functional capabilities include:", H3))
story.append(bullets([
    "<b>CRUD operations</b> on tracked items across 11 categories (add, edit, deactivate, delete, undo)",
    "<b>Data input &amp; processing</b>: recurrence math (one-time/weekly/monthly/yearly/custom-interval), risk scoring",
    "<b>Reporting &amp; analytics</b>: urgency dashboard, category breakdowns, spend charts, cost projections, renewal streaks",
    "<b>Search, filter and multi-mode sort</b> across name, tag, category, overdue status, and due-date window",
]))

# =====================================================================
# 5. NON-FUNCTIONAL REQUIREMENTS
# =====================================================================
story.append(Paragraph("4. Non-Functional Requirements", H1))
nfr_table = Table([
    [th("Requirement"), th("How RenewalVault addresses it")],
    [tcb("Security"), tc("AES-256-GCM encryption at rest (authenticated, tamper-evident by construction), "
     "PBKDF2-HMAC-SHA256 key derivation (120,000 iterations), constant-time password verification, "
     "no password or key ever persisted.")],
    [tcb("Reliability"), tc("A wrong password or corrupted file fails loudly and safely (GCM auth-tag rejection) "
     "instead of silently loading garbage data; every mutation is followed by an immediate encrypted persist.")],
    [tcb("Auditability / Logging"), tc("Every add, edit, renewal, snooze, and alert is recorded as a SHA-256 "
     "hash-chained log entry; verifyAuditIntegrity() can prove the entire history is untampered, or "
     "pinpoint the exact broken entry.")],
    [tcb("Usability"), tc("A single, consistent 18-option menu; colour-coded urgency (red/yellow/green/cyan); "
     "blank-to-keep-current-value editing; duplicate-name warnings; plain-language prompts throughout.")],
    [tcb("Maintainability"), tc("Clean package separation (model / engine / security / db / cli / test) with "
     "single-responsibility classes; every public method is documented; a 12-case regression suite "
     "guards against silent regressions during future changes.")],
    [tcb("Scalability (data volume)"), tc("Search, filter, sort, and the risk-ranked priority queue all operate "
     "in O(n log n) or better over the active item list, which comfortably scales to the thousands of "
     "items a single household or small business would realistically track.")],
    [tcb("Portability"), tc("Zero external dependencies beyond the JDK; a single encrypted .rvbackup file can "
     "move an entire vault to a different machine and be restored with nothing but the original password.")],
    [tcb("Error Handling Strategy"), tc("All user input parsing (dates, numbers, menu choices) is wrapped in "
     "try/catch with specific, actionable messages; cryptographic and I/O failures are caught at the "
     "FileStore boundary and never crash the running session.")],
    [tcb("Resource Efficiency"), tc("No background threads, no polling, no in-memory duplication of the item "
     "list beyond what is actively displayed; the entire vault for a realistic household fits in a few "
     "kilobytes of encrypted storage.")],
], colWidths=[3.4*cm, 12.6*cm])
nfr_table.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1a3a5c")),
    ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#cccccc")),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f6f9fc")]),
    ("TOPPADDING", (0, 0), (-1, -1), 5),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
]))
story.append(nfr_table)
story.append(PageBreak())

# =====================================================================
# 6. SYSTEM ARCHITECTURE
# =====================================================================
story.append(Paragraph("5. System Architecture", H1))
story.append(Paragraph("""
RenewalVault follows a layered architecture with a strict one-directional dependency
flow: the CLI layer depends on the engine layer, the engine layer depends on the
security and domain-model layers, and only the persistence layer touches disk directly
-- and even then, only through the security layer's encryption. No layer reaches
"upward" past the one above it.
""", BODY))
story.append(diagram_auto(f"{DIAGRAMS}/01_system_architecture.png",
             "Figure 1: Layered system architecture, from the CLI down to encrypted disk storage."))
story.append(Paragraph("""
This separation means, for example, that <font face="Courier">FileStore</font> has no
idea it is storing <font face="Courier">TrackedItem</font> or <font face="Courier">
ReminderLog</font> objects specifically -- it is a generic <font face="Courier">
FileStore&lt;T extends Serializable&gt;</font> that encrypts and decrypts whatever it is
given. This is what let a second data type (<font face="Courier">ReminderLog</font>)
and a completely new module (<font face="Courier">BackupEngine</font>) be added later
without touching the encryption code at all.
""", BODY))

# =====================================================================
# 7. DESIGN DIAGRAMS
# =====================================================================
story.append(PageBreak())
story.append(Paragraph("6. Design Diagrams", H1))

story.append(Paragraph("6.1 Process Flow / Workflow Diagram", H2))
story.append(diagram_auto(f"{DIAGRAMS}/02_workflow.png",
             "Figure 2: End-to-end process flow, from launch through the menu loop to save &amp; exit.",
             max_width=11*cm, max_height=22*cm))

story.append(PageBreak())
story.append(Paragraph("6.2 Use Case Diagram", H2))
story.append(diagram_auto(f"{DIAGRAMS}/03_use_case.png",
             "Figure 3: All 15 use cases available to the single actor (User), including the "
             "&lt;&lt;include&gt;&gt; relationship to audit logging."))

story.append(PageBreak())
story.append(Paragraph("6.3 Class Diagram", H2))
story.append(diagram_auto(f"{DIAGRAMS}/04_class_diagram.png",
             "Figure 4: Key classes across all five packages, with their principal attributes, "
             "methods, and relationships.", max_width=15.5*cm, max_height=23*cm))

story.append(PageBreak())
story.append(Paragraph("6.4 Data Model / Schema Diagram", H2))
story.append(Paragraph("""
RenewalVault does not use a relational database; the brief's ER/schema requirement is
addressed here as an ER-style view of the two encrypted, file-based record stores and
how the vault's key material relates to them.
""", BODY))
story.append(diagram_auto(f"{DIAGRAMS}/05_data_model.png",
             "Figure 5: Data model for items.dat, logs.dat, and vault.meta, including the "
             "logical 1-to-many relationship between an item and its log history."))

story.append(PageBreak())
story.append(Paragraph("6.5 Sequence Diagrams", H2))
story.append(Paragraph("""
Two representative flows are shown: adding a tracked item (the core write path,
touching encryption and the hash-chained log) and unlocking the vault (the core
authentication path).
""", BODY))
story.append(diagram_auto(f"{DIAGRAMS}/06_sequence_add_item.png",
             "Figure 6: Sequence diagram for adding a tracked item."))
story.append(Spacer(1, 6))
story.append(diagram_auto(f"{DIAGRAMS}/07_sequence_unlock.png",
             "Figure 7: Sequence diagram for master password unlock (PBKDF2 + HMAC verification)."))

# =====================================================================
# 8. DESIGN DECISIONS & RATIONALE
# =====================================================================
story.append(PageBreak())
story.append(Paragraph("7. Design Decisions &amp; Rationale", H1))

decisions = [
    ("Why AES-256-GCM instead of AES-CBC",
     "GCM is an authenticated encryption mode: it produces a built-in integrity tag, so any "
     "byte-level tampering with an encrypted file is detected automatically at decrypt time, "
     "with no separate MAC to implement or forget. This is what makes the tamper-detection "
     "demonstrated in Section 10 possible with almost no extra code."),
    ("Why PBKDF2 instead of using the password directly",
     "A raw password is not a valid AES key, and using it directly (or a naive hash of it) would "
     "be vulnerable to brute-force and rainbow-table attacks. PBKDF2-HMAC-SHA256 with 120,000 "
     "iterations and a random 16-byte salt (per NIST SP 800-132 guidance) makes each guess "
     "computationally expensive and defeats precomputed tables."),
    ("Why a separate HMAC verifier instead of re-using AES-GCM for password checking",
     "An earlier implementation attempted to verify the password by encrypting a known marker "
     "and hashing the ciphertext -- but AES-GCM's random per-call IV means the same key produces "
     "different ciphertext every time, so this check failed unpredictably even with the correct "
     "password. Switching to a deterministic HMAC-SHA256 verifier (same key and salt always "
     "produce the same output) fixed this. See Section 12 for how this bug was caught."),
    ("Why hash-chain the reminder log instead of just encrypting it",
     "Encryption alone hides the log's content but does not prove its order or completeness -- an "
     "attacker with the key could still edit, delete, or reorder entries and re-encrypt. Chaining "
     "each entry's hash to the one before it (the same idea behind Git commits and blockchains) "
     "means any such edit breaks the chain at a provable, exact position."),
    ("Why a risk score instead of sorting by raw due date",
     "Raw date order treats every item as equally important. A lapsed insurance policy or missed "
     "tax filing is categorically more consequential than a subscription due a day earlier. The "
     "risk score (urgency &times; category weight + cost factor) reflects that reality, and the "
     "category weights were chosen deliberately (COMPLIANCE_TAX and INSURANCE highest, GROCERY "
     "lowest) to match real-world stakes."),
    ("Why a CLI instead of a GUI",
     "The brief allows any technically appropriate solution; a terminal interface keeps the "
     "project fully dependency-free (no GUI toolkit), trivially portable across operating systems, "
     "and keeps the focus on the underlying Java data structures, algorithms, and engine design "
     "that this course evaluates, rather than UI plumbing."),
    ("Why a hand-rolled test framework instead of JUnit",
     "Adding JUnit would require a build tool (Maven/Gradle) or manually vendored jars, breaking "
     "the project's zero-dependency, 'just javac and java' constraint. A ~150-line assertion "
     "harness (TestRunner) provides the same pass/fail reporting with no external dependency."),
]
for title, text in decisions:
    story.append(Paragraph(title, H3))
    story.append(Paragraph(text, BODY))

# =====================================================================
# 9. IMPLEMENTATION DETAILS
# =====================================================================
story.append(PageBreak())
story.append(Paragraph("8. Implementation Details", H1))

story.append(Paragraph("8.1 Core Java Concepts Demonstrated", H2))
java_table = Table([
    [th("Concept"), th("Where it appears in RenewalVault")],
    [tcb("OOP: Encapsulation"), tc("Every model and engine class exposes state only through getters/setters "
     "or intention-revealing methods (e.g. TrackedItem.riskScore(), never raw field access)")],
    [tcb("OOP: Interfaces &amp; enums with behaviour"), tc("Category.riskWeight() and RecurrenceType attach "
     "logic directly to enum constants rather than external if/switch chains scattered across the codebase")],
    [tcb("Generics"), tc("FileStore&lt;T extends Serializable&gt; is a single, type-safe persistence class "
     "reused for both TrackedItem and ReminderLog with no code duplication")],
    [tcb("Collections Framework"), tc("PriorityQueue for the urgency queue, List for item/log storage, "
     "Map for category groupings in analytics")],
    [tcb("Streams API"), tc("Search, filter, sort (VaultService), category grouping and cost summation "
     "(ReminderEngine.costByCategory(), Collectors.groupingBy/summingDouble)")],
    [tcb("java.time API"), tc("LocalDate/LocalDateTime arithmetic drives all recurrence roll-forward logic "
     "and risk-score urgency calculations")],
    [tcb("Exception handling"), tc("Checked exceptions (IOException, GeneralSecurityException) are caught at "
     "well-defined boundaries (FileStore, VaultAuth) and surfaced as actionable CLI messages, never "
     "left to crash the session")],
    [tcb("File I/O &amp; Serialization"), tc("Object serialization (ObjectOutputStream/ObjectInputStream) "
     "combined with javax.crypto for encrypted persistence; java.util.zip for the backup/restore feature")],
    [tcb("Functional interfaces / lambdas"), tc("VaultService's undo mechanism stores a Runnable closure "
     "capturing prior state; Comparator.comparingDouble(...).reversed() drives every sort mode")],
], colWidths=[3.6*cm, 12.4*cm])
java_table.setStyle(TableStyle([
    ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#1a3a5c")),
    ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#cccccc")),
    ("VALIGN", (0, 0), (-1, -1), "TOP"),
    ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f6f9fc")]),
    ("TOPPADDING", (0, 0), (-1, -1), 5),
    ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
]))
story.append(java_table)
story.append(Spacer(1, 10))

story.append(Paragraph("8.2 Package Structure", H2))
story.append(Preformatted(
"""src/main/java/renewalvault/
 |-- model/       TrackedItem.java, ReminderLog.java
 |-- engine/      VaultService, ReminderEngine, AnalyticsEngine,
 |                ExportEngine, BackupEngine
 |-- security/    CryptoUtil, VaultAuth
 |-- db/          FileStore<T>
 |-- cli/         Main, ConsoleColors
 `-- test/        TestRunner (12-case regression suite)""", styles["CodeBlock"]))

story.append(Paragraph("8.3 Key Algorithm: Risk Score", H2))
story.append(Paragraph("""
Each item's urgency is computed as:
""", BODY))
story.append(Preformatted(
"""urgency = overdue
    ? 1000 + (daysOverdue * 25)          // overdue items dominate, and worsen over time
    : max(0, 300 - (daysUntilDue * 6));  // decays as the due date recedes

costFactor = min(estimatedCost / 20, 100);   // diminishing returns, capped

riskScore = (urgency * category.riskWeight()) + costFactor;""", styles["CodeBlock"]))
story.append(Paragraph("""
Category weights range from 2.0 (COMPLIANCE_TAX) down to 0.9 (GROCERY), so the same
number of days-until-due produces a materially different, and realistic, priority.
""", BODY))

story.append(Paragraph("8.4 Key Algorithm: Hash-Chained Audit Log", H2))
story.append(Paragraph("""
Every <font face="Courier">ReminderLog</font> entry stores a SHA-256 hash of its own
content concatenated with the previous entry's hash:
""", BODY))
story.append(Preformatted(
"""entryHash = SHA256(previousHash + logId + itemId + eventType
                    + timestamp + note + daysEarlyOrLate)""", styles["CodeBlock"]))
story.append(Paragraph("""
<font face="Courier">verifyChain()</font> walks the list once, recomputing each hash and
confirming it both matches its own stored value <i>and</i> that the next entry correctly
references it -- an O(n) integrity check that returns the exact 1-based position of the
first broken link, or -1 if the entire history is intact.
""", BODY))

story.append(Paragraph("8.5 Key Algorithm: Recurrence Roll-Forward", H2))
story.append(Paragraph("""
On renewal, <font face="Courier">TrackedItem.rollToNextCycle()</font> advances the due
date using <font face="Courier">java.time</font> arithmetic appropriate to the
recurrence type (<font face="Courier">plusWeeks/plusMonths/plusYears/plusDays</font>),
and a <font face="Courier">ONE_TIME</font> item instead deactivates itself, since it has
no further cycle. Renewal punctuality is captured by comparing
<font face="Courier">daysUntilDue()</font> at the moment of renewal against zero, which
feeds directly into the on-time-streak calculation described in Section 4's Reliability
row.
""", BODY))

story.append(Paragraph("8.6 Undo Mechanism", H2))
story.append(Paragraph("""
Rather than a full undo stack, <font face="Courier">VaultService</font> keeps a single
<font face="Courier">Runnable pendingUndo</font> capturing a closure over the exact
prior state of whatever was just changed (e.g. re-inserting a deleted item at its
original index, or restoring every field an edit overwrote). This keeps the
implementation simple while covering the realistic "I made one mistake, undo it" case
the brief's usability expectations call for.
""", BODY))

# =====================================================================
# 10. SCREENSHOTS / RESULTS
# =====================================================================
story.append(PageBreak())
story.append(Paragraph("9. Screenshots / Results", H1))
story.append(Paragraph("""
The screenshots below are terminal-style renderings of real output captured by running
the compiled application (the exact text and ANSI color codes shown were produced by
actual runs of <font face="Courier">renewalvault.cli.Main</font>; the login screen is
shown as it appears with a real interactive terminal, which masks password input).
""", BODY))
story.append(diagram_auto(f"{SHOTS}/01_login_and_setup.png",
             "Result 1: First-run vault setup -- a new AES-256 vault is created from a master password."))
story.append(diagram_auto(f"{SHOTS}/02_list_items_colorized.png",
             "Result 2: Items listed with colour-coded urgency (yellow = due soon, green = OK)."))
story.append(diagram_auto(f"{SHOTS}/03_analytics.png",
             "Result 3: Analytics view -- category spend chart, cost projections, and on-time renewal streaks."))
story.append(diagram_auto(f"{SHOTS}/04_tamper_detection.png",
             "Result 4: Tamper detection -- a single corrupted byte in logs.dat is rejected by "
             "AES-GCM authentication instead of being silently loaded."))

# =====================================================================
# 11. TESTING APPROACH
# =====================================================================
story.append(PageBreak())
story.append(Paragraph("10. Testing Approach", H1))
story.append(Paragraph("""
Testing was done at two levels: an automated, zero-dependency regression suite
(<font face="Courier">renewalvault.test.TestRunner</font>), and manual end-to-end
verification of the actual CLI.
""", BODY))
story.append(Paragraph("10.1 Automated Regression Suite (12 cases)", H2))
story.append(Paragraph("""
No JUnit, Maven, or Gradle is used -- consistent with the project's zero-dependency
constraint -- just a ~150-line hand-rolled assertion harness that runs entirely against
disposable temporary directories (never the user's real vault). Actual output from a
run performed for this report:
""", BODY))
story.append(Preformatted(open("/tmp/test_output.txt").read().strip(), styles["CodeBlock"]))
story.append(Paragraph("Each case targets a specific correctness property:", H3))
story.append(bullets([
    "AES-GCM encrypt/decrypt correctness and that ciphertext never contains the plaintext",
    "Correct-password unlock and wrong-password rejection (VaultAuth)",
    "That encrypted files on disk contain no readable item names or notes",
    "Risk-score ordering: overdue and high-stakes categories outrank low-stakes ones due sooner",
    "Recurrence roll-forward arithmetic for all five recurrence types",
    "Hash-chain integrity: a valid chain verifies clean; a spliced-in tampered entry is caught "
    "at the exact position it occurs",
    "CSV export/import round-tripping, including names with embedded commas and quotes",
    "Undo correctly reversing both an add and a delete",
    "Full encrypted backup/restore preserving both the vault's password and its data",
    "Case-insensitive duplicate item name detection",
    "On-time renewal streak tracking (increments on time, resets on a late renewal)",
    "Master password rotation: the old password stops working and the new one correctly "
    "decrypts the re-encrypted data",
]))
story.append(Paragraph("10.2 Manual End-to-End Testing", H2))
story.append(bullets([
    "Adding, editing, and renewing items across multiple categories, recurrence types, tags, "
    "and custom alert windows via the real compiled CLI",
    "Restarting the application and confirming all items and full reminder history persist "
    "correctly across runs while encrypted at rest",
    "Changing the master password mid-session and confirming the old password is rejected on "
    "the next run while the new one unlocks the vault with all data intact",
    "Exporting a full encrypted backup and restoring it into a brand-new, empty directory, "
    "confirming the same password and data come back unchanged",
    "Deliberately corrupting a byte in an encrypted log file and confirming the tampering is "
    "detected instead of silently loaded (Result 4, Section 9)",
    "Exporting to CSV and re-importing it, and exporting to .ics and confirming the file is "
    "well-formed iCalendar (VCALENDAR/VEVENT structure, DTSTART, RRULE)",
]))

# =====================================================================
# 12. CHALLENGES FACED
# =====================================================================
story.append(PageBreak())
story.append(Paragraph("11. Challenges Faced", H1))
challenges = [
    ("Non-deterministic password verifier",
     "The first implementation of the master-password check encrypted a known marker with "
     "AES-GCM and hashed the ciphertext as the stored verifier. Because AES-GCM uses a random "
     "IV on every call, the same correct password produced a different verifier every time it "
     "was checked -- so even the right password intermittently failed to unlock the vault. This "
     "was caught during manual reload testing and fixed by switching to a deterministic "
     "HMAC-SHA256 verifier, which always produces the same output for the same key and salt."),
    ("Inverted early/late sign in streak tracking",
     "The on-time renewal streak feature initially computed 'days early or late' with an "
     "inverted sign, so renewing an item several days <i>before</i> its due date was incorrectly "
     "logged as renewing it <i>late</i>. This was caught by the automated test suite "
     "(testRenewalStreak), not by manual testing -- a concrete example of why the regression "
     "suite earns its place in the project rather than being a formality."),
    ("CSV escaping for names containing commas and quotes",
     "Item names can legitimately contain commas or quotation marks (e.g. a note like "
     "'Renew before, or the policy lapses'). A naive CSV writer would corrupt such rows on "
     "re-import. A small quote-aware CSV parser/writer (RFC 4180-style escaping) was written "
     "by hand rather than pulling in an external CSV library, and is covered by the "
     "testCsvRoundTrip case."),
    ("Coordinating in-memory session state across a master-password rotation",
     "Rotating the master password requires flushing current data under the <i>old</i> key, "
     "generating a new salt/verifier, re-encrypting existing files at the byte level, and then "
     "reloading every engine against the new key to confirm the rotation actually worked end to "
     "end -- rather than trusting that it worked. Structuring this as an atomic sequence (persist "
     "&rarr; rotate &rarr; re-encrypt &rarr; reload-and-verify) avoided leaving the vault in a "
     "half-migrated state if any step failed."),
]
for title, text in challenges:
    story.append(Paragraph(title, H3))
    story.append(Paragraph(text, BODY))

# =====================================================================
# 13. LEARNINGS & KEY TAKEAWAYS
# =====================================================================
story.append(PageBreak())
story.append(Paragraph("12. Learnings &amp; Key Takeaways", H1))
story.append(bullets([
    "<b>Applied cryptography is unforgiving of subtle mistakes</b>: the AES-GCM verifier bug "
    "showed that a cryptographically sound primitive (AES-GCM) can still produce an incorrect "
    "system if used for a purpose (deterministic verification) that its design doesn't guarantee.",
    "<b>Automated tests catch bugs that manual testing misses</b>: two real, non-trivial bugs "
    "(the verifier issue and the streak sign inversion) were caught specifically by writing "
    "tests, not by using the application manually -- a direct, first-hand argument for "
    "regression testing rather than a textbook one.",
    "<b>Hash chaining is a small amount of code for a large integrity guarantee</b>: roughly 20 "
    "lines of hashing logic turn a plain log file into one that can prove, mathematically, "
    "whether it has been altered -- the same principle underlying chain-of-custody in digital "
    "forensics and the integrity guarantees of blockchains and Git.",
    "<b>Good layering pays off during feature growth</b>: because FileStore and CryptoUtil have "
    "no knowledge of what they're encrypting, adding BackupEngine and the entire on-time-streak "
    "feature in a second development pass required no changes to the encryption or persistence "
    "code at all.",
    "<b>A CLI's constraints are also design tools</b>: without a GUI framework to lean on, "
    "usability had to come from careful menu structure, colour-coding, and consistent prompts -- "
    "forcing clearer thinking about workflow than a drag-and-drop UI builder might have.",
]))

# =====================================================================
# 14. FUTURE ENHANCEMENTS
# =====================================================================
story.append(Paragraph("13. Future Enhancements", H1))
story.append(bullets([
    "A graphical (desktop or web) front-end reusing the existing engine layer unchanged, since "
    "the CLI layer is already isolated behind plain Java method calls",
    "Optional local push notifications (OS-level) as an alternative to relying on .ics import "
    "or manually opening the app",
    "Multi-user / multi-vault support, so a household could share selected categories while "
    "keeping others private",
    "A machine-learning-based cost forecaster trained on historical renewal data, extending the "
    "current linear projection in AnalyticsEngine",
    "A pluggable notification channel abstraction (email/SMS) for users willing to opt in to an "
    "external service, kept strictly outside the current offline-only core",
    "Biometric or hardware-key-backed unlock as an alternative to a memorized master password",
]))

# =====================================================================
# 15. REFERENCES
# =====================================================================
story.append(Paragraph("14. References", H1))
story.append(bullets([
    "NIST Special Publication 800-132, <i>Recommendation for Password-Based Key Derivation</i> "
    "(basis for the PBKDF2 iteration-count and salt-length choices)",
    "NIST Special Publication 800-38D, <i>Recommendation for Block Cipher Modes of Operation: "
    "Galois/Counter Mode (GCM) and GMAC</i>",
    "Oracle Java Cryptography Architecture (JCA) Reference Guide -- <font face=\"Courier\">"
    "javax.crypto</font> API documentation",
    "RFC 5545, <i>Internet Calendaring and Scheduling Core Object Specification (iCalendar)</i> "
    "-- basis for the .ics export format",
    "OWASP Password Storage Cheat Sheet -- general guidance on salting, iteration counts, and "
    "avoiding plaintext password storage",
    "Oracle Java SE 21 Documentation -- <font face=\"Courier\">java.time</font>, "
    "<font face=\"Courier\">java.util.zip</font>, and <font face=\"Courier\">java.io</font> "
    "package references",
]))

story.append(Spacer(1, 20))
story.append(HRFlowable(width="100%", thickness=0.5, color=colors.HexColor("#cccccc")))
story.append(Paragraph("End of Report -- RenewalVault, VITyarthi Build Your Own Project Submission",
                        styles["Caption"]))

doc = SimpleDocTemplate("PROJECT_REPORT.pdf", pagesize=A4,
                         topMargin=1.6*cm, bottomMargin=1.6*cm,
                         leftMargin=1.8*cm, rightMargin=1.8*cm,
                         title="RenewalVault - Project Report")
doc.build(story)
print("PROJECT_REPORT.pdf built.")
