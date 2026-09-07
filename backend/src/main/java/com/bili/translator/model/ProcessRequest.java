package com.bili.translator.model;

public class ProcessRequest {

    private String url;
<<<<<<< HEAD
    private String sourceLanguage;
=======
>>>>>>> trae/solo-agent-DQFIa2
    private String targetLanguage;
    private boolean translateSubtitles = true;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

<<<<<<< HEAD
    public String getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

=======
>>>>>>> trae/solo-agent-DQFIa2
    public String getTargetLanguage() {
        return targetLanguage;
    }

    public void setTargetLanguage(String targetLanguage) {
        this.targetLanguage = targetLanguage;
    }

    public boolean isTranslateSubtitles() {
        return translateSubtitles;
    }

    public void setTranslateSubtitles(boolean translateSubtitles) {
        this.translateSubtitles = translateSubtitles;
    }
}
