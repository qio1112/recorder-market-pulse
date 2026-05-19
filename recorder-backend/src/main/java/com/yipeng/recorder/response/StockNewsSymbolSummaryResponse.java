package com.yipeng.recorder.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class StockNewsSymbolSummaryResponse {

    private String symbol;
    private String summary;
    @JsonProperty("article_count")
    private Integer articleCount;
    private List<StockNewsArticleResponse> articles = new ArrayList<>();

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Integer getArticleCount() {
        return articleCount;
    }

    public void setArticleCount(Integer articleCount) {
        this.articleCount = articleCount;
    }

    public List<StockNewsArticleResponse> getArticles() {
        return articles;
    }

    public void setArticles(List<StockNewsArticleResponse> articles) {
        this.articles = articles;
    }
}
