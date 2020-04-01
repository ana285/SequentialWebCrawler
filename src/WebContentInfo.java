public class WebContentInfo {
    private String code;

    @Override
    public String toString() {
        return "WebContentInfo{" +
                "code='" + code + '\'' +
                ", redirectLocation='" + redirectLocation + '\'' +
                ", content='" + content + '\'' +
                '}';
    }

    private String redirectLocation;
    private String content;

    WebContentInfo(){
        code = null;
        redirectLocation = null;
        content = null;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getRedirectLocation() {
        return redirectLocation;
    }

    public void setRedirectLocation(String redirectLocation) {
        this.redirectLocation = redirectLocation;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

}
