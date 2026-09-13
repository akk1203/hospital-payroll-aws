# Environment setup — hospital-payroll-aws

This folder is a **separate Git repo**. Path:

`C:\Users\akkat\Projects\hospital-payroll\hospital-payroll-aws`

A push to **`main`** starts CodePipeline. CodeBuild compiles the Quarkus Java 21 Lambda, then **`sam deploy` creates/updates the test-stage AWS stack** (`hospital-payroll-test`): HTTP API, SnapStart Lambda, DynamoDB (on-demand), S3 website UI, Cognito.

You create pipeline infrastructure **once**. Application infrastructure is created by the first successful pipeline run.

---

## 0. What you need

| Tool | Why |
| --- | --- |
| AWS account + IAM user/role with admin (or enough to create IAM, CloudFormation, CodePipeline, CodeBuild, S3) | One-time pipeline stack |
| [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) | Deploy the pipeline stack |
| Git | Local repo |
| GitHub account | Remote `main` branch |
| Optional: [GitHub CLI `gh`](https://cli.github.com/) | Create the GitHub repo from the terminal |
| Optional locally: Java 21 + Maven | Only if you build on your PC; CodeBuild already has them |

Default AWS region used below: **`ap-southeast-2`** (Sydney). Use this region in the console, Connections, CLI, and every stack name lookup. Lambda SnapStart for Java 21 works there.

---

## 1. AWS CLI on this machine

```powershell
aws --version
aws configure
```

Enter:

- Access key and secret for your IAM user
- Default region: `ap-southeast-2`
- Output: `json`

Check:

```powershell
aws sts get-caller-identity
```

Note **Account** and **Arn**. You will use the account id in the Cognito domain prefix.

---

## 2. Create the GitHub repository (empty)

GitHub website: **New repository** → name `hospital-payroll-aws` → **do not** add a README.

Or:

```powershell
cd C:\Users\akkat\Projects\hospital-payroll\hospital-payroll-aws
gh auth login
gh repo create hospital-payroll-aws --private --source=. --remote=origin --push
```

Skip `--push` if you have not committed yet. You will push in step 6.

Owner example: `https://github.com/YOUR_GITHUB_USER/hospital-payroll-aws`

---

## 3. One-time AWS infra — GitHub connection

CodePipeline cannot read GitHub until a **Connection** exists and is **Available**.

1. AWS Console → **Developer Tools** → **Connections** (CodeConnections / CodeStar Connections).
2. **Create connection** → **GitHub**.
3. Install/authorize the AWS Connector GitHub App on the `hospital-payroll-aws` repo (or the whole org).
4. Wait until status is **Available**.
5. Copy the connection ARN. It looks like:

`arn:aws:codeconnections:ap-southeast-2:123456789012:connection/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`

(older accounts may show `arn:aws:codestar-connections:...` — both work)

---

## 4. One-time AWS infra — pipeline stack

This stack creates:

- S3 artifact bucket
- IAM roles for CodePipeline and CodeBuild
- CodeBuild project (Java 21, SAM CLI)
- CodePipeline: **Source `main` → Build + deploy test**

Pick a **globally unique** Cognito domain prefix (letters/digits/hyphens only), for example `payroll-test-123456789012`.

```powershell
cd C:\Users\akkat\Projects\hospital-payroll\hospital-payroll-aws

$Region = "ap-southeast-2"
$GitHubOwner = "YOUR_GITHUB_USER"
$ConnectionArn = "arn:aws:codeconnections:ap-southeast-2:ACCOUNT_ID:connection/CONNECTION_ID"
$CognitoPrefix = "payroll-test-ACCOUNT_ID"

aws cloudformation deploy `
  --region $Region `
  --template-file pipeline/pipeline.yaml `
  --stack-name hospital-payroll-aws-pipeline `
  --capabilities CAPABILITY_IAM `
  --parameter-overrides `
    GitHubOwner=$GitHubOwner `
    GitHubRepo=hospital-payroll-aws `
    GitHubBranch=main `
    ConnectionArn=$ConnectionArn `
    AppStackName=hospital-payroll-test `
    EnvironmentName=test `
    CognitoDomainPrefix=$CognitoPrefix
```

Leave `GoogleClientId` / `GoogleClientSecret` empty until you add Google sign-in.

Outputs:

```powershell
aws cloudformation describe-stacks --stack-name hospital-payroll-aws-pipeline --query "Stacks[0].Outputs"
```

Open **PipelineUrl** in the console. The first run may fail until `main` exists on GitHub — that is expected.

---

## 5. Local Git repo (this folder only)

```powershell
cd C:\Users\akkat\Projects\hospital-payroll\hospital-payroll-aws
git init -b main
git add .
git status
git commit -m "Initial Quarkus Lambda SnapStart app and test pipeline."
git remote add origin https://github.com/YOUR_GITHUB_USER/hospital-payroll-aws.git
```

If `gh repo create ... --remote=origin` already added `origin`, skip `git remote add`.

---

## 6. Push `main` — this triggers build and test deploy

```powershell
git push -u origin main
```

Then:

1. CodePipeline **Source** pulls `main`.
2. CodeBuild runs `mvn package` (Quarkus `function.zip`).
3. `sam deploy` creates/updates **`hospital-payroll-test`**:
   - API Gateway HTTP API
   - Java 21 Lambda + SnapStart alias `live`
   - DynamoDB on-demand tables
   - Data S3 + public S3 website for the UI
   - Cognito user pool
4. UI files sync to the UI bucket (`ui/config.js` points at the test API).

Watch:

```powershell
aws codepipeline get-pipeline-state --name hospital-payroll-aws-pipeline-main-to-test
```

Or the console **PipelineUrl**.

First SnapStart publish can take several minutes after Lambda is created.

Application outputs:

```powershell
aws cloudformation describe-stacks --stack-name hospital-payroll-test --query "Stacks[0].Outputs"
```

Open **UiUrl**. Sign up with email/password. Demo reset stays enabled on `test` (it is off only for `prod`).

---

## 7. Later pushes

Every later `git push origin main` repeats build + **update** of the test stack. No extra CloudFormation for the pipeline unless you change `pipeline/pipeline.yaml` (then re-run step 4).

---

## Fast deploy (skip the 3–4 minute pipeline)

Use this on your PC when you are iterating. It does **not** wait for CodePipeline. Infra (Cognito, DynamoDB, API Gateway, S3) stays as-is.

Install once: [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html), [Java 21](https://adoptium.net/), [Maven 3.9+](https://maven.apache.org/download.cgi). `aws configure` region **`ap-southeast-2`**.

Windows blocks `.ps1` scripts by default. Use **Command Prompt** or double-click `deploy-fast.cmd` (not the `.ps1`).

```bat
cd C:\Users\akkat\Projects\hospital-payroll\hospital-payroll-aws

REM UI CSS/JS only (about 10 seconds)
deploy-fast.cmd -What ui

REM API only (Maven package, upload zip to S3, Lambda SnapStart alias live)
deploy-fast.cmd -What api

REM Both
deploy-fast.cmd
```

If you still want PowerShell:

```powershell
cd C:\Users\akkat\Projects\hospital-payroll\hospital-payroll-aws
powershell -ExecutionPolicy Bypass -File .\scripts\deploy-fast.ps1 -What ui
```

After an API change, open the S3 UI, sign in, and click **Calculate** again so payslips use the new rules.

Push `main` when you want the pipeline to record the same revision on AWS.

---

## Optional — Google sign-in on test

1. Google Cloud Console → OAuth client (Web).
2. Authorized redirect URI:

`https://YOUR_COGNITO_PREFIX.auth.ap-southeast-2.amazoncognito.com/oauth2/idpresponse`

3. Re-deploy the **pipeline** stack with `GoogleClientId` and `GoogleClientSecret`. CodeBuild passes them into `sam deploy` on the next `main` push.

---

## Optional — build on your PC (not required for AWS)

Install Temurin/Corretto **21** and Maven 3.9+.

```powershell
cd C:\Users\akkat\Projects\hospital-payroll\hospital-payroll-aws
mvn -f api/pom.xml -DskipTests package
```

That only produces `api/target/function.zip`. AWS test is still deployed by the pipeline.

---

## Stacks you should see in CloudFormation

| Stack | Created by | Purpose |
| --- | --- | --- |
| `hospital-payroll-aws-pipeline` | You, step 4 | Pipeline, CodeBuild, artifact bucket |
| `hospital-payroll-test` | Pipeline on push to `main` | Test app (API, Lambda SnapStart, DynamoDB, UI) |
| `aws-sam-cli-managed-default` (name may vary) | First `sam deploy` | SAM upload bucket |

Do not commit secrets. Cognito client secret is stored in Lambda environment by CloudFormation, not in Git.
