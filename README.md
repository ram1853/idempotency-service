## Project structure

| Directory | Files |
| --------- | ----- |
| .github.  | github worklows defined for actions |
| infra.    | terraform scripts to provision the AWS infrastructure |
| worker/src/main | backend source codes that gets deployed to AWS Lambda |
| worker/src/test | junit test cases |

---

# Stacks used

## Infrastructure in AWS

| Service | Purpose |
| ------  | ------  |
| API-Gateway | Internet facing REST APIs |
| Lambda      | Backend processing |
| IAM         | For Roles and Permissions |
| VPC and Subnets | For isolated networks |
| Security Groups | Restrict Inbound/Outbound Rules |
| Valkey Cache    | Redis based Elasticache for storing Idempotency-Keys and body |

## Infrastructure Provisioning

Terraform scripts are used to provision the complete infrastructure. The components are provisioned in 3 Availability zones to ensure maximum availability.

---

## CI-CD

GitHub wokflow triggers when a push is made to 'main' branch - which builds the code, runs the unit tests, provisions the infrastructure and deploys the application.

---

## Programming Languages and Frameworks
* Java 17
* Spring Boot 3.5.13 (maven based)
* Junit 5
* Jacoco Plugin for code coverage
* Other dependencies as needed

---

## Steps to build the codebase
* Make sure Java 17 and Maven is installed.
* Run `mvn clean package` - This will generate 2 jars:
  * fat jar - for AWS Lambda Deployment
  * thin jar - to test locally
* Run `mvn clean test` to run unit test cases and the coverage report can be found in "target/site/index.html"
