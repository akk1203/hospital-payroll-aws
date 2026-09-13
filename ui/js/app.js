function api() {
    const configured = window.PAYROLL_API || "http://localhost:8080/api";
    if (configured.startsWith("/")) {
        return configured.replace(/\/$/, "");
    }
    try {
        const url = new URL(configured, location.origin);
        if (isLoopbackHost(url.hostname) && !isLoopbackHost(location.hostname)) {
            url.hostname = location.hostname;
        }
        return (url.origin + url.pathname).replace(/\/$/, "");
    } catch (error) {
        return configured.replace(/\/$/, "");
    }
}

function isLoopbackHost(host) {
    return host === "localhost" || host === "127.0.0.1" || host === "[::1]" || host === "::1";
}

async function request(path, options = {}) {
    const headers = { ...(options.headers || {}) };
    if (options.body && !(options.body instanceof FormData) && !headers["Content-Type"]) {
        headers["Content-Type"] = "application/json";
    }
    const token = localStorage.getItem("payroll_token");
    if (token && !headers.Authorization) {
        headers.Authorization = "Bearer " + token;
    }
    const response = await fetch(api() + path, { ...options, headers });
    if (response.status === 401 && !path.startsWith("/auth/")) {
        logout(false);
        throw new Error("Please log in to continue");
    }
    if (response.status === 204) {
        return null;
    }
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
        throw new Error((data && data.error) || response.statusText);
    }
    return data;
}

async function downloadAuth(path, filename) {
    const headers = {};
    const token = localStorage.getItem("payroll_token");
    if (token) {
        headers.Authorization = "Bearer " + token;
    }
    const response = await fetch(api() + path, { headers });
    if (!response.ok) {
        throw new Error("Download failed");
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
}

function bindExportLinks() {
    document.querySelectorAll(".export-link").forEach((link) => {
        link.addEventListener("click", async (event) => {
            event.preventDefault();
            const id = link.getAttribute("data-id");
            await downloadAuth("/payroll/" + id + "/export", "payslip-" + id + ".xlsx");
        });
    });
}

function apiRoot() {
    return api().replace(/\/api$/, "");
}

function currentUser() {
    try {
        return JSON.parse(localStorage.getItem("payroll_user") || "null");
    } catch (error) {
        return null;
    }
}

function isLoggedIn() {
    return Boolean(localStorage.getItem("payroll_token"));
}

function saveSession(auth) {
    localStorage.setItem("payroll_token", auth.token);
    localStorage.setItem("payroll_user", JSON.stringify({ email: auth.email, name: auth.name }));
}

function logout(redirect = true) {
    localStorage.removeItem("payroll_token");
    localStorage.removeItem("payroll_user");
    if (redirect) {
        location.hash = "#/login";
        render();
    }
}

function hashPath() {
    return (location.hash || "#/").split("?")[0];
}

function hashQuery() {
    return new URLSearchParams((location.hash.split("?")[1] || ""));
}

function isPublicPage() {
    const path = hashPath();
    return path === "#/login" || path === "#/signup" || path === "#/auth/callback";
}

function googleLoginUrl(config) {
    return config && config.googleLoginUrl ? config.googleLoginUrl : "#/login";
}

function money(value) {
    const amount = Number(value || 0);
    return "₹ " + amount.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function flash(message, kind) {
    return `<div class="flash ${kind}">${escapeHtml(message)}</div>`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;");
}

function setActiveNav() {
    const hash = location.hash || "#/";
    document.querySelectorAll("nav a").forEach((link) => {
        link.classList.toggle("active", hash.startsWith(link.getAttribute("href")));
    });
}

async function refreshDemoMenu() {
    const nav = document.getElementById("main-nav") || document.querySelector("header nav");
    if (!nav) {
        return;
    }
    nav.querySelectorAll(".nav-reset").forEach((item) => item.remove());
    if (!isLoggedIn()) {
        return;
    }
    try {
        const demo = await request("/demo");
        if (!demo || !demo.resetEnabled) {
            return;
        }
        const button = document.createElement("button");
        button.type = "button";
        button.className = "nav-reset";
        button.textContent = "Reset demo data";
        button.addEventListener("click", resetDemoData);
        nav.appendChild(button);
    } catch (error) {
        // Menu stays hidden when the API is down or this is production.
    }
}

async function resetDemoData() {
    const confirmed = confirm(
        "Delete attendance, mappings, day adjustments, and payslips for this demo?\n\nEmployees will be kept. This cannot be undone."
    );
    if (!confirmed) {
        return;
    }
    try {
        const result = await request("/demo/data", { method: "DELETE" });
        alert(result && result.message ? result.message : "Demo data cleared. Employees were kept.");
        location.hash = "#/";
        render();
    } catch (error) {
        alert(error.message);
    }
}

function refreshAccount() {
    const slot = document.getElementById("account");
    const nav = document.getElementById("main-nav");
    if (nav) {
        nav.hidden = !isLoggedIn();
    }
    if (!slot) {
        return;
    }
    if (!isLoggedIn()) {
        slot.innerHTML = `<a class="btn secondary" href="#/login">Log in</a>`;
        return;
    }
    const user = currentUser() || {};
    slot.innerHTML = `
        <span class="who">${escapeHtml(user.name || user.email || "Signed in")}</span>
        <button type="button" class="btn secondary" id="logout-btn">Log out</button>`;
    const button = document.getElementById("logout-btn");
    if (button) {
        button.addEventListener("click", () => logout(true));
    }
}

async function render() {
    const app = document.getElementById("app");
    const hash = location.hash || "#/";
    if (hash.includes("id_token=")) {
        const params = new URLSearchParams(hash.startsWith("#") ? hash.slice(1) : hash);
        const token = params.get("id_token");
        if (token) {
            localStorage.setItem("payroll_token", token);
            try {
                const me = await request("/auth/me");
                saveSession({ token, email: me.email, name: me.name });
            } catch (error) {
                saveSession({ token, email: "", name: "Signed in" });
            }
            location.hash = "#/";
            return;
        }
    }
    if (hashPath() === "#/auth/callback") {
        const token = hashQuery().get("token");
        if (token) {
            localStorage.setItem("payroll_token", token);
            try {
                const me = await request("/auth/me");
                saveSession({ token, email: me.email, name: me.name });
            } catch (error) {
                saveSession({ token, email: "", name: "Signed in" });
            }
            location.hash = "#/";
            return;
        }
        location.hash = "#/login?error=google";
        return;
    }
    if (!isLoggedIn() && !isPublicPage()) {
        location.hash = "#/login";
        return;
    }
    if (isLoggedIn() && (hashPath() === "#/login" || hashPath() === "#/signup")) {
        location.hash = "#/";
        return;
    }
    setActiveNav();
    refreshAccount();
    refreshDemoMenu();
    try {
        if (hashPath() === "#/login") {
            app.innerHTML = await loginView();
            bindLoginForm();
        } else if (hashPath() === "#/signup") {
            app.innerHTML = await signupView();
            bindSignupForm();
        } else if (hash === "#/" || hash === "") {
            app.innerHTML = await homeView();
        } else if (hash.startsWith("#/employees/edit/")) {
            app.innerHTML = await employeeFormView(hash.replace("#/employees/edit/", ""));
            bindEmployeeForm();
        } else if (hash === "#/employees/new") {
            app.innerHTML = await employeeFormView(null);
            bindEmployeeForm();
        } else if (hash === "#/employees") {
            app.innerHTML = await employeesView();
            bindEmployeeList();
        } else if (hash.startsWith("#/attendance/")) {
            app.innerHTML = await attendanceDetailView(hash.replace("#/attendance/", ""));
            bindMappingForms();
        } else if (hash === "#/attendance") {
            app.innerHTML = await attendanceView();
            bindUpload();
        } else if (hash.startsWith("#/payroll/")) {
            app.innerHTML = await payslipDetailView(hash.replace("#/payroll/", "").split("?")[0]);
            bindPayslipDetail();
        } else if (hash.startsWith("#/payroll")) {
            app.innerHTML = await payrollView();
            bindPayroll();
        } else {
            app.innerHTML = "<h1>Not found</h1>";
        }
    } catch (error) {
        app.innerHTML = flash(error.message, "bad") + "<p>Is the API reachable? Check <code>ui/config.js</code>.</p>";
    }
}

async function authConfig() {
    try {
        return await request("/auth/config");
    } catch (error) {
        return { googleEnabled: false };
    }
}

function googleButton(config) {
    return `<a class="btn google" href="${escapeHtml(googleLoginUrl(config))}">Continue with Google</a>`;
}

async function loginView() {
    const config = await authConfig();
    const error = hashQuery().get("error");
    const banner = error === "google"
        ? flash("Google sign-in was cancelled or failed. You can try again or use email.", "bad")
        : "";
    return `
        <form class="form auth" id="login-form">
            <h1>Log in</h1>
            <p class="lead">Use your hospital account, or sign in with Gmail.</p>
            ${banner}
            <div id="login-error"></div>
            <label>Email <input type="email" name="email" required autocomplete="username"/></label>
            <label>Password <input type="password" name="password" required autocomplete="current-password"/></label>
            <button class="btn" type="submit">Log in</button>
            ${config.googleEnabled ? `<p class="or-line">or</p>${googleButton(config)}` : ""}
            <p>New here? <a href="#/signup">Create an account</a></p>
        </form>`;
}

async function signupView() {
    const config = await authConfig();
    return `
        <form class="form auth" id="signup-form">
            <h1>Create account</h1>
            <p class="lead">Sign up with email, or use your Gmail account.</p>
            <div id="signup-error"></div>
            <label>Full name <input name="name" required autocomplete="name"/></label>
            <label>Email <input type="email" name="email" required autocomplete="username"/></label>
            <label>Password <input type="password" name="password" required minlength="8" autocomplete="new-password"/></label>
            <button class="btn" type="submit">Sign up</button>
            ${config.googleEnabled ? `<p class="or-line">or</p>${googleButton(config)}` : ""}
            <p>Already have an account? <a href="#/login">Log in</a></p>
        </form>`;
}

function bindLoginForm() {
    const form = document.getElementById("login-form");
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const errorBox = document.getElementById("login-error");
        const data = Object.fromEntries(new FormData(form).entries());
        try {
            const auth = await request("/auth/login", { method: "POST", body: JSON.stringify(data) });
            saveSession(auth);
            location.hash = "#/";
            render();
        } catch (error) {
            errorBox.innerHTML = flash(error.message, "bad");
        }
    });
}

function bindSignupForm() {
    const form = document.getElementById("signup-form");
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const errorBox = document.getElementById("signup-error");
        const data = Object.fromEntries(new FormData(form).entries());
        try {
            const auth = await request("/auth/signup", { method: "POST", body: JSON.stringify(data) });
            saveSession(auth);
            location.hash = "#/";
            render();
        } catch (error) {
            errorBox.innerHTML = flash(error.message, "bad");
        }
    });
}

async function homeView() {
    const [employees, batches] = await Promise.all([
        request("/employees"),
        request("/attendance")
    ]);
    const rows = batches.map((batch) => `
        <tr>
            <td data-label="File">${escapeHtml(batch.originalFilename)}</td>
            <td data-label="Period">${escapeHtml(batch.periodStart)} → ${escapeHtml(batch.periodEnd)}</td>
            <td data-label="Mapped">${batch.mappedPeople}/${(batch.people || []).length}</td>
            <td data-label="Actions"><a href="#/attendance/${batch.id}">Open</a></td>
        </tr>`).join("") || `<tr><td class="empty" colspan="4">No attendance files yet.</td></tr>`;
    return `
        <h1>HR payroll for the hospital</h1>
        <p class="lead">Create employees, upload the biometric attendance report, map names from the file, then calculate monthly salary in INR.</p>
        <div class="cards">
            <a class="card" href="#/employees"><h2>Employees</h2><p>${employees.length} on file</p></a>
            <a class="card" href="#/attendance"><h2>Attendance</h2><p>Upload CSV or Excel and map sheet names.</p></a>
            <a class="card" href="#/payroll"><h2>Payroll</h2><p>Paid leave, LWP deduction, net pay.</p></a>
        </div>
        <h2>Recent uploads</h2>
        <div class="table-wrap">
        <table class="stack">
            <thead><tr><th>File</th><th>Period</th><th>Mapped</th><th></th></tr></thead>
            <tbody>${rows}</tbody>
        </table>
        </div>`;
}

async function employeesView() {
    const employees = await request("/employees");
    const rows = employees.map((emp) => `
        <tr>
            <td data-label="Name">${escapeHtml(emp.name)}</td>
            <td data-label="Position">${escapeHtml(emp.position || "")}</td>
            <td data-label="Joined">${escapeHtml(emp.dateOfJoining || "")}</td>
            <td data-label="Salary / month">${money(emp.salaryPerMonth)}</td>
            <td data-label="Hours / day">${escapeHtml(emp.hoursPerDay ?? "")}</td>
            <td data-label="Allowed leaves">${emp.allowedLeavesPerMonth}</td>
            <td data-label="Sheet ID">${escapeHtml(emp.attendanceCode || "")}</td>
            <td class="inline actions" data-label="Actions">
                <a class="btn secondary" href="#/employees/edit/${emp.id}">Edit</a>
                <button class="btn danger" data-deactivate="${emp.id}">Remove</button>
            </td>
        </tr>`).join("") || `<tr><td class="empty" colspan="8">No employees yet.</td></tr>`;
    return `
        <div class="row">
            <h1>Employees</h1>
            <a class="btn" href="#/employees/new">Add employee</a>
        </div>
        <div class="table-wrap">
        <table class="stack">
            <thead>
                <tr>
                    <th>Name</th><th>Position</th><th>Joined</th>
                    <th>Salary / month</th><th>Hours / day</th><th>Allowed leaves</th><th>Sheet ID</th><th></th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
        </div>`;
}

function bindEmployeeList() {
    document.querySelectorAll("[data-deactivate]").forEach((button) => {
        button.addEventListener("click", async () => {
            if (!confirm("Deactivate this employee?")) {
                return;
            }
            try {
                await request("/employees/" + button.dataset.deactivate, { method: "DELETE" });
                location.hash = "#/employees";
                render();
            } catch (error) {
                alert(error.message);
            }
        });
    });
}

async function employeeFormView(id) {
    const employee = id ? await request("/employees/" + id) : {
        name: "", position: "", department: "", dateOfJoining: "", salaryPerMonth: "", hoursPerDay: 8, allowedLeavesPerMonth: 2, attendanceCode: ""
    };
    return `
        <h1>${id ? "Edit employee" : "New employee"}</h1>
        <form class="form" id="employee-form" data-id="${id || ""}">
            <label>Full name <input name="name" required value="${escapeHtml(employee.name || "")}"/></label>
            <label>Position <input name="position" value="${escapeHtml(employee.position || "")}" placeholder="Staff Nurse, Consultant"/></label>
            <label>Department <input name="department" value="${escapeHtml(employee.department || "")}"/></label>
            <label>Date of joining <input type="date" name="dateOfJoining" required value="${escapeHtml(employee.dateOfJoining || "")}"/></label>
            <label>Salary per month (INR) <input type="number" step="0.01" min="0" name="salaryPerMonth" required value="${escapeHtml(employee.salaryPerMonth ?? "")}"/></label>
            <label>Hours per day <input type="number" step="0.25" min="0.25" name="hoursPerDay" required value="${escapeHtml(employee.hoursPerDay ?? 8)}"/></label>
            <label>Allowed leaves per month <input type="number" min="0" name="allowedLeavesPerMonth" value="${escapeHtml(employee.allowedLeavesPerMonth ?? 0)}"/></label>
            <label>Attendance sheet ID <input name="attendanceCode" value="${escapeHtml(employee.attendanceCode || "")}" placeholder="Matches printed ID, e.g. 6"/></label>
            <div class="inline">
                <button class="btn" type="submit">Save</button>
                <a href="#/employees">Cancel</a>
            </div>
        </form>`;
}

function bindEmployeeForm() {
    const form = document.getElementById("employee-form");
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const data = Object.fromEntries(new FormData(form).entries());
        data.salaryPerMonth = Number(data.salaryPerMonth);
        data.hoursPerDay = Number(data.hoursPerDay);
        data.allowedLeavesPerMonth = Number(data.allowedLeavesPerMonth || 0);
        const id = form.dataset.id;
        try {
            if (id) {
                await request("/employees/" + id, { method: "PUT", body: JSON.stringify(data) });
            } else {
                await request("/employees", { method: "POST", body: JSON.stringify(data) });
            }
            location.hash = "#/employees";
        } catch (error) {
            alert(error.message);
        }
    });
}

async function attendanceView() {
    const batches = await request("/attendance");
    const rows = batches.map((batch) => `
        <tr>
            <td data-label="File">${escapeHtml(batch.originalFilename)}</td>
            <td data-label="Period">${escapeHtml(batch.periodStart)} → ${escapeHtml(batch.periodEnd)}</td>
            <td data-label="People">${(batch.people && batch.people.length) || ((batch.mappedPeople || 0) + (batch.unmappedPeople || 0))}</td>
            <td data-label="Unmapped">${batch.unmappedPeople}</td>
            <td data-label="Actions"><a class="btn secondary" href="#/attendance/${batch.id}">Map employees</a></td>
        </tr>`).join("") || `<tr><td class="empty" colspan="5">No uploads yet.</td></tr>`;
    return `
        <h1>Attendance import</h1>
        <p>Upload the printed <em>Attendance Record Report</em> Excel (ID column, calendar-day columns, in time on the first row and out time on the second row). CSV with <code>time_in</code>/<code>time_out</code> still works.</p>
        <p>
            <a href="#" id="dl-printed">Download Dr Dharmik &amp; Moinbhai Excel</a>
            ·
            <a href="#" id="dl-sample">Download sample CSV</a>
        </p>
        <form class="form" id="upload-form">
            <label>Attendance file <input type="file" name="file" accept=".csv,.xlsx,.xls" required/></label>
            <button class="btn" type="submit">Upload and map</button>
        </form>
        <h2>Uploaded files</h2>
        <div class="table-wrap">
        <table class="stack">
            <thead><tr><th>File</th><th>Period</th><th>People</th><th>Unmapped</th><th></th></tr></thead>
            <tbody>${rows}</tbody>
        </table>
        </div>`;
}

function bindUpload() {
    document.getElementById("dl-printed")?.addEventListener("click", async (event) => {
        event.preventDefault();
        await downloadAuth("/attendance/templates/printed", "Attendance-Record-Report-Dharmik-Moin.xlsx");
    });
    document.getElementById("dl-sample")?.addEventListener("click", async (event) => {
        event.preventDefault();
        await downloadAuth("/attendance/templates/sample", "attendance-sample.csv");
    });
    document.getElementById("upload-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        const file = event.target.file.files[0];
        const buffer = await file.arrayBuffer();
        const bytes = new Uint8Array(buffer);
        let binary = "";
        bytes.forEach((b) => { binary += String.fromCharCode(b); });
        const contentBase64 = btoa(binary);
        try {
            const batch = await request("/attendance/upload", {
                method: "POST",
                body: JSON.stringify({ filename: file.name, contentBase64 })
            });
            location.hash = "#/attendance/" + batch.id;
        } catch (error) {
            alert(error.message);
        }
    });
}

async function attendanceDetailView(id) {
    const [batch, employees] = await Promise.all([
        request("/attendance/" + id),
        request("/employees")
    ]);
    const options = employees.map((emp) =>
        `<option value="${escapeHtml(emp.id)}">${escapeHtml(emp.name)} — ${escapeHtml(emp.position || "")}</option>`
    ).join("");
    const rows = (batch.people || []).map((person) => `
        <tr>
            <td data-label="Sheet ID">${escapeHtml(person.sourceEmployeeCode || "")}</td>
            <td data-label="Name on sheet">${escapeHtml(person.sourceEmployeeName || "")}</td>
            <td data-label="Days">${(person.days || []).length}</td>
            <td data-label="Matched">${person.mapped
                ? `<span class="ok-text">${escapeHtml(person.mappedEmployeeName)} (${escapeHtml(person.matchReason)})</span>`
                : `<span class="warn">Not mapped</span>`}</td>
            <td data-label="Map">
                <form class="inline map-form">
                    <input type="hidden" name="sourceKey" value="${escapeHtml(person.sourceKey)}"/>
                    <select name="employeeId" required>
                        <option value="">Select employee</option>
                        ${employees.map((emp) =>
                            `<option value="${escapeHtml(emp.id)}" ${emp.id === person.mappedEmployeeId ? "selected" : ""}>${escapeHtml(emp.name)} — ${escapeHtml(emp.position || "")}</option>`
                        ).join("")}
                    </select>
                    <button type="submit">Save</button>
                </form>
            </td>
        </tr>`).join("");
    return `
        <h1>Map file names to employees</h1>
        <p>File: ${escapeHtml(batch.originalFilename)} · ${escapeHtml(batch.periodStart)} to ${escapeHtml(batch.periodEnd)}</p>
        <p>${batch.mappedPeople} mapped, ${batch.unmappedPeople} still need a match.</p>
        <div class="table-wrap">
        <table class="stack">
            <thead><tr><th>Sheet ID</th><th>Name on sheet</th><th>Days</th><th>Matched employee</th><th>Map</th></tr></thead>
            <tbody>${rows}</tbody>
        </table>
        </div>
        <p><a class="btn" href="#/payroll">Calculate salary</a></p>`;
}

function bindMappingForms() {
    const id = location.hash.replace("#/attendance/", "");
    document.querySelectorAll(".map-form").forEach((form) => {
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const data = new FormData(form);
            const params = new URLSearchParams({
                sourceKey: data.get("sourceKey"),
                employeeId: data.get("employeeId")
            });
            try {
                await request("/attendance/" + id + "/map?" + params.toString(), { method: "POST" });
                render();
            } catch (error) {
                alert(error.message);
            }
        });
    });
}

async function payrollView() {
    const batches = await request("/attendance");
    const hashMonth = new URLSearchParams(location.hash.split("?")[1] || "").get("month");
    const month = hashMonth
        || (batches[0] && batches[0].periodStart ? String(batches[0].periodStart).slice(0, 7) : "")
        || new Date().toISOString().slice(0, 7);
    const payslips = await request("/payroll?month=" + encodeURIComponent(month));
    const options = batches.map((batch) =>
        `<option value="${escapeHtml(batch.id)}">${escapeHtml(batch.originalFilename)} (${escapeHtml(batch.periodStart)} to ${escapeHtml(batch.periodEnd)})</option>`
    ).join("");
    const rows = (payslips || []).map((slip) => `
        <tr>
            <td data-label="Employee">${escapeHtml(slip.employeeName)}</td>
            <td data-label="Position">${escapeHtml(slip.position || "")}</td>
            <td data-label="Present">${slip.presentDays}</td>
            <td data-label="Hours worked">${Number(slip.workedHours || 0).toFixed(2)}h</td>
            <td data-label="Payable hours">${Number(slip.payableHours || 0).toFixed(2)}h</td>
            <td data-label="Rate / hour">${money(slip.hourlyRate)}</td>
            <td data-label="Gross">${money(slip.monthlySalary)}</td>
            <td data-label="Shortfall">${money(slip.leaveWithoutPayDeduction)}</td>
            <td data-label="Net pay">${money(slip.netPay)}</td>
            <td class="inline actions" data-label="Actions">
                <a class="btn secondary" href="#/payroll/${escapeHtml(slip.id)}">Daily breakdown</a>
                <a class="btn export-link" href="#" data-id="${escapeHtml(slip.id)}">Excel</a>
            </td>
        </tr>`).join("") || `<tr><td class="empty" colspan="10">No payslips for ${escapeHtml(month)} yet. Choose that month and click Calculate.</td></tr>`;
    return `
        <h1>Monthly salary</h1>
        <p>Hourly rate = monthly salary ÷ ((sheet days − allowed leaves) × hours per day). Allowed leave is built into the rate, so those days do not need extra paid-leave hours. Net pay = hourly rate × hours credited on present days. Extra hours above the daily requirement are paid at the same rate.</p>
        <p>Days you change on the daily breakdown are kept if you upload the same month again and recalculate. The file will not overwrite those hours.</p>
        <form class="form" id="payroll-form">
            <label>Attendance file
                <select name="batchId" required>
                    <option value="">Select upload</option>
                    ${options}
                </select>
            </label>
            <label>Payroll month <input type="month" name="month" value="${escapeHtml(month)}" required/></label>
            <button class="btn" type="submit">Calculate</button>
        </form>
        <p class="lead">Showing payslips for <strong>${escapeHtml(month)}</strong>.</p>
        <div class="table-wrap">
        <table class="stack">
            <thead>
                <tr>
                    <th>Employee</th><th>Position</th><th>Present</th><th>Hours worked</th>
                    <th>Payable hours</th><th>Rate / hour</th><th>Gross</th><th>Shortfall</th><th>Net pay</th><th></th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
        </div>`;
}

function bindPayroll() {
    bindExportLinks();
    const form = document.getElementById("payroll-form");
    form.month.addEventListener("change", () => {
        location.hash = "#/payroll?month=" + form.month.value;
    });
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const data = new FormData(form);
        const month = data.get("month");
        const params = new URLSearchParams({
            batchId: data.get("batchId"),
            month
        });
        try {
            await request("/payroll/calculate?" + params.toString(), { method: "POST" });
            const next = "#/payroll?month=" + month;
            if (location.hash === next) {
                render();
            } else {
                location.hash = next;
            }
        } catch (error) {
            alert(error.message);
        }
    });
}

function toTimeInput(value) {
    if (!value) {
        return "";
    }
    const match = String(value).match(/^(\d{1,2}):(\d{2})/);
    if (!match) {
        return "";
    }
    return String(match[1]).padStart(2, "0") + ":" + match[2];
}

function weekdayName(value) {
    if (!value) {
        return "";
    }
    const parts = String(value).split("-");
    if (parts.length !== 3) {
        return "";
    }
    const date = new Date(Number(parts[0]), Number(parts[1]) - 1, Number(parts[2]));
    return date.toLocaleDateString("en-IN", { weekday: "long" });
}

function isIncompletePunch(day) {
    if (!day) {
        return false;
    }
    if (day.incompletePunch) {
        return true;
    }
    const hasIn = Boolean(toTimeInput(day.timeIn));
    const hasOut = Boolean(toTimeInput(day.timeOut));
    return hasIn !== hasOut;
}

async function payslipDetailView(id) {
    const detail = await request("/payroll/" + id);
    const slip = detail.payslip;
    const month = slip.month;
    const rows = (detail.days || []).map((day) => {
        const incomplete = isIncompletePunch(day);
        const weekday = day.weekday || weekdayName(day.date);
        const classes = [
            day.adjusted ? "row-adjusted" : "",
            incomplete ? "row-incomplete" : "",
            weekday === "Sunday" ? "row-sunday" : ""
        ].filter(Boolean).join(" ");
        const tags = [
            day.adjusted ? `<span class="tag tag-adjusted">Corrected</span>` : "",
            incomplete ? `<span class="tag tag-incomplete">Missing in/out</span>` : ""
        ].join("");
        return `
        <tr class="${classes}">
            <td data-label="Date">${escapeHtml(day.date)}${tags}</td>
            <td data-label="Day"><span class="${weekday === "Sunday" ? "sunday-name" : ""}">${escapeHtml(weekday)}</span></td>
            <td data-label="Adjust">
                <form class="inline day-form" data-date="${escapeHtml(day.date)}">
                    <input type="time" name="timeIn" value="${toTimeInput(day.timeIn)}" title="In" aria-label="Time in"/>
                    <input type="time" name="timeOut" value="${toTimeInput(day.timeOut)}" title="Out" aria-label="Time out"/>
                    <select name="status" aria-label="Status">
                        <option value="PRESENT" ${day.status === "PRESENT" ? "selected" : ""}>Present</option>
                        <option value="LEAVE" ${day.status === "LEAVE" ? "selected" : ""}>Leave</option>
                        <option value="ABSENT" ${day.status === "ABSENT" ? "selected" : ""}>Absent</option>
                    </select>
                    <select name="credit" aria-label="Credit">
                        <option value="WORKED" ${day.credit === "WORKED" ? "selected" : ""}>Actual hours</option>
                        <option value="FULL_DAY" ${day.credit === "FULL_DAY" ? "selected" : ""}>Full day</option>
                        <option value="HALF_DAY" ${day.credit === "HALF_DAY" ? "selected" : ""}>Half day</option>
                    </select>
                    <button type="submit">Save</button>
                </form>
            </td>
            <td data-label="Punched">${Number(day.punchedHours || 0).toFixed(2)}h</td>
            <td data-label="Credited">${Number(day.creditedHours || 0).toFixed(2)}h</td>
            <td data-label="Pay">${money(day.pay)}</td>
        </tr>`;
    }).join("");
    return `
        <p><a href="#/payroll?month=${encodeURIComponent(month)}">← Monthly salary</a>
            · <a class="btn export-link" href="#" data-id="${escapeHtml(id)}">Export Excel</a></p>
        <h1>${escapeHtml(slip.employeeName)}</h1>
        <p class="lead">${escapeHtml(slip.position || "")} · ${escapeHtml(month)} ·
            ${Number(slip.hoursPerDay || 0)} hours/day · ${money(slip.hourlyRate)} / hour ·
            Net pay ${money(slip.netPay)}</p>
        <p>Change in/out times, or mark a day as <strong>full day</strong> (${escapeHtml(slip.hoursPerDay)}h) or
            <strong>half day</strong> (${(Number(slip.hoursPerDay || 0) / 2).toFixed(2)}h). Salary is recalculated when you save.
            These changes stay if you re-upload attendance and calculate this month again.</p>
        <p class="legend">
            <span><i class="swatch adjusted"></i> Corrected in the UI</span>
            <span><i class="swatch incomplete"></i> Only in or only out</span>
        </p>
        <div class="table-wrap">
        <table class="stack">
            <thead>
                <tr>
                    <th>Date</th><th>Day</th><th>Adjust attendance</th><th>Punched</th><th>Credited</th><th>Pay</th>
                </tr>
            </thead>
            <tbody>${rows || `<tr><td class="empty" colspan="6">No days in this payslip.</td></tr>`}</tbody>
        </table>
        </div>`;
}

function bindPayslipDetail() {
    bindExportLinks();
    const id = location.hash.replace("#/payroll/", "").split("?")[0];
    document.querySelectorAll(".day-form").forEach((form) => {
        form.addEventListener("submit", async (event) => {
            event.preventDefault();
            const data = Object.fromEntries(new FormData(form).entries());
            try {
                await request("/payroll/" + id + "/days", {
                    method: "PUT",
                    body: JSON.stringify({
                        date: form.dataset.date,
                        timeIn: data.timeIn,
                        timeOut: data.timeOut,
                        status: data.status,
                        credit: data.credit
                    })
                });
                render();
            } catch (error) {
                alert(error.message);
            }
        });
    });
}

window.addEventListener("hashchange", render);
window.addEventListener("load", render);
