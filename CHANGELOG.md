# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.5.0] - 2025-06-13

### Changed

- Update parent from 26.55.0 to 26.57.0

## [2.4.0] - 2025-06-06

### Changed

- Update parent from 26.43.2 to 26.55.0

## [2.3.0] - 2025-04-15

### Changed

- Update parent from 26.42.0 to 26.43.2

## [2.2.0] - 2025-04-01

### Changed

- Update parent from 26.33.0 to 26.42.0

## [2.1.0] - 2025-03-06

### Changed

- Update parent from 26.24.2 to 26.33.0

## [2.0.0] - 2025-02-26

### Changed
- **BREAKING** - Removed default values for the following Spring properties:
  - `jeap.deploymentlog.jira.app-id`
  - `jeap.deploymentlog.jira.url`
  - `jeap.deploymentlog.documentation.root-url`

## [1.56.0] - 2025-02-13

### Changed

- Update parent from 26.23.0 to 26.24.2
- Disable license plugins for service instances

## [1.55.1] - 2025-02-11

### Changed

- Publish to maven central

## [1.55.0] - 2025-02-10

### Changed

- Update parent from 26.22.3 to 26.23.0

## [1.54.0] - 2025-02-03

### Changed

- Prepare repository for Open Source distribution

## [1.53.3] - 2025-01-23

### Fixed

- Fix template and lock name for undeployments

### Changed

- Update the rest endpoint to generate all pages for a system for a year

## [1.53.2] - 2025-01-23

### Fixed

- If regenerating confluence page for a system, render undeployments as UndeploymentLetter 

## [1.53.1] - 2025-01-23

### Fixed

- Update the PageId in the database if the DeploymentLetterPage updated in confluence

## [1.53.0] - 2025-01-23

### Changed

- Add new rest endpoint to repair jira links to deployment letters for a system

## [1.52.0] - 2025-01-20

### Changed

- System Service: search for system by name is case-insensitive

## [1.51.1] - 2025-01-15

### Fixed

- Fix System not found by alias for previous and current version checks

## [1.51.0] - 2025-01-14

### Changed

- Added the module jeap-deploymentlog-service-instance which will instantiate a jEAP deploymentlog service instance when used as parent project.
- Update parent from 26.21.1 to 26.22.3.

## [1.50.0] - 2024-12-19

### Changed

- Update parent from 26.17.0 to 26.21.1

## [1.49.0] - 2024-12-02

### Changed

- Update parent from 26.6.1 to 26.17.0
- 
### Added

- new function to merge two existing systems

## [1.48.0] - 2024-11-05

### Changed

- Update parent from 26.5.0 to 26.6.1

### Added

- New SystemAlias entity to support systems with multiple names

## [1.47.0] - 2024-10-31

### Changed

- Update parent from 26.4.0 to 26.5.0

## [1.46.0] - 2024-10-17

### Changed

- Update parent from 26.3.0 to 26.4.0

## [1.45.0] - 2024-09-25

### Changed

- Add references, link build jobs using reference if found

## [1.44.0] - 2024-09-20

### Changed

- Update parent from 26.0.0 to 26.3.0

## [1.43.0] - 2024-09-06

### Changed

- Update parent from 25.4.0 to 26.0.0

## [1.42.0] - 2024-08-22

### Changed

- Update parent from 25.1.0 to 25.4.0

## [1.41.0] - 2024-07-29

### Changed

- Update parent from 24.5.0 to 25.1.0

## [1.40.1] - 2024-07-18

### Changed

- Add flyway postgres dependency

## [1.40.0] - 2024-07-16

### Changed

- Update parent from 23.22.1 to 24.5.0

## [1.39.0] - 2024-06-21

### Changed

- Fixed exception on get /api/system/{systemName} 
- Upgraded jEAP parent to 23.22.1

## [1.38.1] - 2024-05-30

### Changed

- Add primary keys to tables without PK as a preparation for data replication to AWS

## [1.38.0] - 2024-03-28

### Changed

- Update parent from 23.10.4 to 23.12.0

## [1.37.0] - 2024-03-14

### Changed

- Update parent from 23.10.1 to 23.10.4

## [1.36.0] - 2024-03-06

### Added

- Support adding properties to a deployment, and render properties on the deployment letter page

## [1.35.0] - 2024-03-05

### Added
- New API operation: Get previous deployment of component of system on environment that is different to the version param

## [1.34.0] - 2024-03-05

### Changed

- Updated jeap parent from 23.0.0 to 23.10.0
- Switched from WebClient to RestClient, removed reactive dependencies in application (still using WebTestClient in tests, though)

## [1.33.0] - 2024-02-05

### Changed

- Update parent from 22.5.0 to 23.0.0

## [1.32.0] - 2024-01-25

### Changed

- Update parent from 22.4.0 to 22.5.0

## [1.31.0] - 2024-01-25

### Changed

- Update parent from 22.2.3 to 22.4.0

## [1.30.0] - 2024-01-23

### Changed

- Update parent from 22.1.0 to 22.2.3

## [1.29.0] - 2024-01-16

### Changed

- Update parent from 22.0.0 to 22.1.0

## [1.28.0] - 2024-01-03

### Changed

- Update parent from 21.2.0 to 22.0.0
- remove bootstrap configuration

## [1.27.1] - 2024-01-05

### Added

- Add job resource to generate pages for a system

## [1.27.0] - 2023-12-15

### Changed

- Add API resource for deployed components on an environment
- Update parent from 21.0.0 to 21.2.0

## [1.26.0] - 2023-11-22

### Changed

- Update parent from 20.6.0 to 21.0.0

## [1.25.0] - 2023-10-30

### Changed

- Updated links and information in OpenApi configuration

## [1.24.0] - 2023-09-21

### Changed

- Update parent from 20.0.0 to 20.6.0
- Added new undeployment feature: 
  - Components undeployments can be notified and they will be accordingly registered in the database
  - Confluence pages will be updated

## [1.23.1] - 2023-08-24

### Changed

- Fix time zone issues: Wrong time zone mapped by Hibernate 6 for existing entities

## [1.23.0] - 2023-08-16

### Changed

- Update parent from 19.17.0 to 20.0.0 (Spring Boot 3)
- Spring Boot 3 Migration

## [1.22.0] - 2023-08-09

### Changed

- Update parent from 19.16.1 to 19.17.0

## [1.21.0] - 2023-08-08

### Changed

- Update parent from 19.12.2 to 19.16.1

## [1.20.0] - 2023-06-19

### Changed

- Update parent from 19.12.1 to 19.12.2

### Added

- Deployment History Overview Page: overview of all deployments by environment

## [1.19.0] - 2023-05-30

### Changed

- Update parent from 19.10.1 to 19.12.1

## [1.18.0] - 2023-04-21

### Changed

- Update parent from 19.2.0 to 19.10.1

## [1.17.1] - 2023-02-24

### Changed

- Improve logging on confluence post errors for blogposts: Return error message & status code, log warning

## [1.17.0] - 2023-02-21

### Changed

- Update parent from 19.0.0 to 19.2.0

## [1.16.0] - 2023-02-14

### Added

- Add new Confluence custom rest client to create blogpost

## [1.15.0] - 2023-02-01

### Added

- DeploymentTarget to deployment entity to store the platform used for the deployment

### Changed

- Update jeap-spring-boot-parent from 18.4.0 to 19.0.0

## [1.14.0] - 2022-12-02

### Added

- Add new service to check the labels of issues in jira when creating a new deployment
- Add the jira issue number in white text to be able to search it in confluence

### Changed

- Refactoring jira adapter

## [1.13.0] - 2022-11-25

### Changed

- Add jira api to update issues with confluence link

## [1.12.0] - 2022-11-18

### Changed

- Update parent from 18.3.0 to 18.4.0
- Add new ArtifactVersion entity

## [1.11.0] - 2022-11-15

### Changed

- Update parent from 18.2.0 to 18.3.0
- Add attribute remedyChangeId to deployment entity

## [1.10.1] - 2022-11-09

### Fixed

- Swagger documentation for the DeploymentDocController

## [1.10.0] - 2022-11-08

### Added

- Rest controller that redirects from the external id of a deployment to the deployment page
- Indicate first deployment in change log on the confluence page 
- Rest method to retrieve the previous installed version of an environment

## [1.9.1] - 2022-11-03

### Changed

- Add missing retry annotation

## [1.9.0] - 2022-11-01

### Added

- Retry with sleep on Confluence connection timeouts

## [1.8.0] - 2022-10-31

### Changed

- Update parent from 18.1.0 to 18.2.0

## [1.7.0] - 2022-10-26

### Changed

- Ensure doc gen is not running concurrently on multiple nodes

## [1.6.1] - 2022-10-25

### Changed

- Set SecurityFilterChain bean to the same order as the deprecated WebSecurityConfigurerAdapter was before.
- Retry on confluence page update conflict

## [1.6.0] - 2022-10-04

### Changed

- Update parent from 17.3.0 to 18.0.0 (spring boot 2.7)

## [1.5.0] - 2022-09-21

### Changed

- Update parent from 17.2.2 to 17.3.0

## [1.4.0] - 2022-09-13

### Changed

- Update parent from 17.0.0 to 17.2.2

## [1.3.0] - 2022-07-12

### Changed

- Generate deployment page title with sortable date prefix

## [1.2.7] - 2022-05-11

### Changed

- Sort components by name on systempage

## [1.2.6] - 2022-05-09

### Fixed

- Improved transaction handling / JPA model to avoid out-of-memory situations

## [1.2.5] - 2022-05-06

### Changed

- Reverted transactional annotation on generatorservice due to OOM issues

## [1.2.4] - 2022-05-06

### Changed

- Add REST enpoints to generate single deployment pages and to trigger housekeeping

## [1.2.3] - 2022-05-05

### Changed

- Apply deployment page limit for nonprod environments per environment and not over all envs
- Make sure to keep last deployment page, and all pages up to the last succesful deployment
- Re-generate deployment history page after deployment page housekeeping to avoid dead links

## [1.2.2] - 2022-04-05

### Fixed

- Remove is_snapshot column from component_version as it is unused and can lead to not-null constraint violations

## [1.2.1] - 2022-03-21

### Fixed

- Fix system component state legend color

## [1.2.0] - 2022-03-18

### Changed

- Ignore dev envs in stage coloring
- Misses next stage: yellow color
- Make confluence page delete idempotent

## [1.1.1] - 2022-03-17

### Changed

- Upgrade to latest jeap parent

## [1.0.1] - 2022-03-15

### Changed

- Fix some minor findings

## [1.0.0] - 2022-03-10

### Changed

- Initial version
