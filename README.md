<div align="center">

# 🚗 AutoRent — Car Rental Management System

### A full-stack web application built with Java & Spring Boot

[![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=java)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.5-green?style=for-the-badge&logo=springboot)](https://spring.io/projects/spring-boot)
[![H2 Database](https://img.shields.io/badge/Database-H2-blue?style=for-the-badge&logo=h2)](https://h2database.com)
[![Maven](https://img.shields.io/badge/Maven-3.9-red?style=for-the-badge&logo=apachemaven)](https://maven.apache.org)
[![License](https://img.shields.io/badge/License-MIT-purple?style=for-the-badge)](LICENSE)

**[🌐 Live Demo](https://web-production-da248.up.railway.app) · [📁 Source Code](https://github.com/bikash-20/rental-car-java-project) · <img width="1280" height="731" alt="image" src="https://github.com/user-attachments/assets/ac9fc050-15a7-4797-adca-5f277363d4f2" />


</div>

---

## 📌 Overview

**AutoRent** is a fully functional Car Rental Management System developed as a 2nd-year Java project. It evolved from a basic console application into a production-ready full-stack web application, demonstrating real-world software development skills including layered architecture, database persistence, PDF generation, and data visualization.

> The project was first built as a **JavaFX desktop application**, then converted into a **Spring Boot web application** accessible from any device including mobile.

---

## ✨ Features

| Feature | Description |
|---|---|
| 📊 **Dashboard** | Live stats — total cars, available, rented, revenue |
| 🔑 **Rent a Car** | Date picker with live price preview and double-booking prevention |
| ↩️ **Return a Car** | Return any active rental with one click |
| 📋 **Rental History** | Full record of active and completed rentals |
| ⬇️ **PDF Receipts** | Download professional PDF receipt for any booking |
| 📈 **Revenue Charts** | Monthly revenue bar chart + category doughnut chart |
| ⚙️ **Manage Fleet** | Add new cars or delete existing ones from the UI |
| 🗄️ **Database** | H2 persistent database — data survives server restarts |
| 📱 **Mobile Friendly** | Fully responsive — works on phone, tablet, and desktop |

---

## 🛠️ Tech Stack

### Backend
- **Java 17** — Core programming language
- **Spring Boot 3.2.5** — Web framework + dependency injection
- **Spring Data JPA** — Database ORM layer
- **H2 Database** — Embedded persistent SQL database
- **iText PDF 5** — PDF receipt generation

### Frontend
- **Thymeleaf** — Server-side HTML templating
- **HTML5 + CSS3** — Mobile-first responsive design
- **Vanilla JavaScript** — Live price preview, date validation
- **Chart.js** — Interactive revenue and category charts

### Tools & Deployment
- **Maven** — Dependency management and build tool
- **Git + GitHub** — Version control
- **Railway.app** — Cloud deployment (CI/CD from GitHub)

---

## 🏗️ Project Architecture

```
AutoRentWeb/
├── src/main/java/carrental/
│   ├── App.java                         ← Spring Boot entry point
│   ├── model/
│   │   ├── Car.java                     ← JPA entity
│   │   ├── Customer.java                ← JPA entity
│   │   └── Rental.java                  ← JPA entity with date fields
│   ├── repository/
│   │   ├── CarRepository.java           ← Spring Data JPA
│   │   ├── CustomerRepository.java      ← Spring Data JPA
│   │   └── RentalRepository.java        ← Custom overlap query
│   ├── service/
│   │   ├── CarRentalSystem.java         ← Business logic layer
│   │   └── PdfService.java              ← PDF generation with iText
│   └── controller/
│       └── RentalController.java        ← HTTP routes + request handling
└── src/main/resources/
    ├── templates/                        ← Thymeleaf HTML pages
    │   ├── layout.html                  ← Shared navbar + footer
    │   ├── dashboard.html
    │   ├── rent.html
    │   ├── rent-success.html
    │   ├── return.html
    │   ├── return-success.html
    │   ├── history.html
    │   ├── charts.html
    │   └── cars.html
    ├── static/
    │   ├── css/style.css                ← Mobile-first dark theme
    │   └── js/app.js                    ← Date logic + price preview
    └── application.properties           ← DB + server config
```

### Design Pattern
This project follows a clean **3-layer architecture**:
```
Controller (HTTP) → Service (Business Logic) → Repository (Database)
```

---

## 🚀 Getting Started

### Prerequisites
| Tool | Version | Download |
|---|---|---|
| Java JDK | 17+ | https://adoptium.net |
| Maven | 3.8+ | https://maven.apache.org |

### Run Locally
```bash
# Clone the repository
git clone https://github.com/bikash-20/rental-car-java-project.git
cd rental-car-java-project

# Run the application
mvn spring-boot:run
```

Open your browser → **http://localhost:8080**

### Build JAR
```bash
mvn package
java -jar target/AutoRentWeb-1.0.jar
```

---

## 📸 Screenshots

### Dashboard
> Live fleet overview with stat cards showing total cars, availability, and revenue

### Rent a Car
> Date-based booking with real-time price calculation and double-booking prevention

### PDF Receipt
> Professionally designed PDF receipt downloadable after every booking

### Revenue Charts
> Monthly revenue bar chart and category-wise doughnut chart powered by Chart.js

---

## 📈 Project Evolution

This project was built in stages to demonstrate progressive learning:

```
Stage 1 — Console App          Basic Java OOP, Scanner input
      ↓
Stage 2 — JavaFX Desktop App   GUI with dark theme, CSS styling
      ↓
Stage 3 — Spring Boot Web App  Full-stack, REST routes, Thymeleaf
      ↓
Stage 4 — Database + Features  JPA persistence, PDF, Charts, Date booking
```

---

## 🔮 Future Enhancements

- [ ] User authentication (Admin + Customer login)
- [ ] Email confirmation on booking
- [ ] PostgreSQL for production database
- [ ] Android mobile application
- [ ] Car photo uploads
- [ ] Discount and coupon system

---

## 👨‍💻 Author

<div align="center">

**Bikash Talukder**

2nd Year Computer Science Student

[![GitHub](https://img.shields.io/badge/GitHub-bikash--20-black?style=for-the-badge&logo=github)](https://github.com/bikash-20)

</div>

---

<div align="center">

**⭐ If you found this project useful, please give it a star on GitHub!**

*Built with ❤️ using Java + Spring Boot*

</div>
