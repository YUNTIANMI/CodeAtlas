package com.codeatlas.knowledge.dto;

import java.util.List;

/**
 * RAG 问答结果：答案 + 引用来源 + 模型标识。
 */
public class AskAnswerVO {

    private String answer;

    private List<CitationVO> citations;

    private String model;

    public AskAnswerVO() {
    }

    public AskAnswerVO(String answer, List<CitationVO> citations, String model) {
        this.answer = answer;
        this.citations = citations;
        this.model = model;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<CitationVO> getCitations() {
        return citations;
    }

    public void setCitations(List<CitationVO> citations) {
        this.citations = citations;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
