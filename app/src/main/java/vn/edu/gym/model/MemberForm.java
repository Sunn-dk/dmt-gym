package vn.edu.gym.model;

import java.time.LocalDate;

public class MemberForm {
    private String fullName;
    private String phone;
    private LocalDate birthDate;

    public MemberForm() {
    }

    public MemberForm(String fullName, String phone, LocalDate birthDate) {
        this.fullName = fullName;
        this.phone = phone;
        this.birthDate = birthDate;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }
}
