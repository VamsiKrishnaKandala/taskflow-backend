# 🧩 TaskFlow Pro — Backend

Reactive, microservice-based employee task management platform built with **Spring Boot WebFlux**.

## 🏗️ Architecture

Each service is reactive and uses:
- Functional endpoints (RouterFunctions + HandlerFunctions)
- R2DBC for non-blocking DB access
- Reactive WebClient for inter-service calls
- Eureka for service discovery
- Spring Cloud Gateway for routing

## 📂 Microservices
- `eureka-server` — service registry
- `api-gateway` — reactive API routing
- `user-service` — user & role management
- `project-service` — project & team management
- `task-service` — task lifecycle, assignment, history
- `notification-service` — real-time alerts (SSE)
- `analytics-service` — metrics & dashboard data