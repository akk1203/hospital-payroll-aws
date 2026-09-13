# Hospital payroll on AWS

Quarkus **Java 21** Lambda with **SnapStart**, HTTP API, DynamoDB on-demand, S3 website UI, Cognito.

This directory is its own Git repository. **Push to `main` builds on AWS and deploys the test stage.**

For day-to-day changes, use **fast deploy** instead of waiting 3–4 minutes for CodePipeline: [SETUP.md](SETUP.md#fast-deploy-skip-the-34-minute-pipeline).

```bat
deploy-fast.cmd -What ui
deploy-fast.cmd
```

Full walkthrough (tools, GitHub connection, pipeline stack, first push): **[SETUP.md](SETUP.md)**.
