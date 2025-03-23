# Interconnections Flight Service

This project implements an API to search for flight connections between two airports within a given time frame. It leverages reactive programming using `Flux` for parallelization and applies stream-like operations on reactive flows.

## Features

- **Endpoint**: `/interconnections`
- **Required parameters**:
  - `departure` (IATA code of the departure airport)
  - `arrival` (IATA code of the arrival airport)
  - `departureDateTime` (ISO 8601 format, e.g., `2025-03-25T06:00`)
  - `arrivalDateTime` (ISO 8601 format, e.g., `2025-03-25T18:10`)
- **Parallel processing**: Uses `Flux` to efficiently retrieve and process flight schedules.
- **Error handling**: Validations and custom exceptions for missing routes or schedules.
- **Automated testing**: Unit tests validate business logic and ensure API reliability.
- **CI/CD Pipeline**: GitHub Actions automate the build and artifact generation, following the **"You build it, you run it"** principle.

## Running the application

Download the artifact from the latest build and start the application by running:

```sh
java -jar jar_name.jar
