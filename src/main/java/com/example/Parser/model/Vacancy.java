package com.example.Parser.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "vacancies",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_vacancy_url",
                columnNames = "url"
        )
)
public class Vacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String title;

    @Column(length = 500)
    private String company;

    @Column(length = 500)
    private String salary;

    @Column(length = 10_000)
    private String requirements;

    @Column(length = 500)
    private String city;

    @Column(length = 100)
    private String publicationDate;

    @Column(nullable = false, length = 1000)
    private String url;

    public Vacancy() {
    }

    public Vacancy(
            String title,
            String company,
            String salary,
            String requirements,
            String city,
            String publicationDate,
            String url
    ) {
        this.title = title;
        this.company = company;
        this.salary = salary;
        this.requirements = requirements;
        this.city = city;
        this.publicationDate = publicationDate;
        this.url = url;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCompany() {
        return company;
    }

    public String getSalary() {
        return salary;
    }

    public String getRequirements() {
        return requirements;
    }

    public String getCity() {
        return city;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public String getUrl() {
        return url;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public void setRequirements(String requirements) {
        this.requirements = requirements;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "Vacancy{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", company='" + company + '\'' +
                ", salary='" + salary + '\'' +
                ", city='" + city + '\'' +
                ", publicationDate='" + publicationDate + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
