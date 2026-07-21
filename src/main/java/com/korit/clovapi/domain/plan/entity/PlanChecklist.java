package com.korit.clovapi.domain.plan.entity;

public class PlanChecklist {

    private Long id;
    private Long planId;
    private String content;
    private Boolean checked;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Boolean getChecked() { return checked; }
    public void setChecked(Boolean checked) { this.checked = checked; }
}
