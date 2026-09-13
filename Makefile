# Build Quarkus function.zip (Java 21 + SnapStart-ready JVM)
package:
	mvn -f api/pom.xml -DskipTests package

# First-time / update AWS stack. CognitoDomainPrefix must be globally unique.
deploy: package
	sam deploy --guided --capabilities CAPABILITY_IAM

sync-ui:
	@echo "Set UI_BUCKET and API_URL then: printf 'window.PAYROLL_API = \"%s/api\";\\n' $$API_URL > ui/config.js && aws s3 sync ui/ s3://$$UI_BUCKET"
