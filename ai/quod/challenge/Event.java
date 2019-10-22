package ai.quod.challenge;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

 
class Event {
    public enum Type {
        PushEvent,
        IssuesEvent,
        PullRequestEvent,
        DoNotCare
    }

    public Event(String _jsonString) throws JSONException {
		JSONObject obj = new JSONObject(_jsonString);
        id = obj.getLong("id");
        type = toType(obj.getString("type"));
		actorId = obj.getJSONObject("actor").getLong("id");
        {
            JSONObject repoObj = obj.getJSONObject("repo");
            String repoPath = repoObj.getString("name");
            String[] parts = repoPath.split("/",2);
            repo = new Repo(repoObj.getLong("id"), parts[0], parts[1]);
        }
        zonedDateTime = ZonedDateTime.parse(obj.getString("created_at"));
    }

    public long getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public Repo getRepo() {
        return repo;
    }

    public long getActorId() {
        return actorId;
    }
    
    public ZonedDateTime getZonedDateTime() {
        return zonedDateTime;
    }

    private Type toType(String s) {
        switch (s) {
            case "PushEvent":
                return Type.PushEvent;
            case "IssuesEvent":
                return Type.IssuesEvent; 
            case "PullRequestEvent":
                return Type.PullRequestEvent;
            default:
                break;
        }
        
        return Type.DoNotCare; 
    }


    private long id; 
    private Type type;
    private Repo repo; 
    private long actorId; 
    private ZonedDateTime zonedDateTime;
}

class Repo {
    public Repo(long _id, String _orgName, String _name) {
        id = _id;
        orgName = _orgName;
        name = _name;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    } 

    public String getOrgName() {
        return orgName;
    }

    private long id;
    private String name;
    private String orgName;
}

class PushEventPayLoad {
    public PushEventPayLoad(String _jsonString) throws JSONException {
		JSONObject obj = new JSONObject(_jsonString);
        numberOfDistinctCommit = obj.getJSONObject("payload").getInt("distinct_size");
    }

    public int getNumberOfDistinctCommit() {
        return numberOfDistinctCommit;
    }

    private int numberOfDistinctCommit;
}

class IssuesEventPayLoad {
    public enum Action {
        Opened,
        Closed,
        Reopened,
        DoNotCare 
    }

    public IssuesEventPayLoad(String _jsonString) throws JSONException {
		JSONObject obj = new JSONObject(_jsonString);
        JSONObject payLoadObj = obj.getJSONObject("payload");
        action = toAction(payLoadObj.getString("action"));
        issueId = payLoadObj.getJSONObject("issue").getLong("id");
    }

    public Action getAction() {
        return action;
    }

    public long getIssueId() {
        return issueId;
    }

    private Action toAction(String s) {
        switch (s) {
            case "opened":
                return Action.Opened;

            case "closed":
                return Action.Closed;

            case "reopened":
                return Action.Reopened;

            default:
                break;
        }

        return Action.DoNotCare;
    }

    private Action action;
    private long issueId;
}

class PullRequestEventPayLoad {
    public enum Action {
        Opened,
        Closed,
        DoNotCare
    }
     
    public PullRequestEventPayLoad(String _jsonString) throws JSONException {
		JSONObject obj = new JSONObject(_jsonString);
        JSONObject payLoadObj = obj.getJSONObject("payload");
        action = toAction(payLoadObj.getString("action")); 
        pullRequestId = payLoadObj.getJSONObject("pull_request").getLong("id");
        merged = payLoadObj.getBoolean("merged");
    }

    public Action getAction() {
        return action;
    }
    
    public long getPullRequestId() {
        return pullRequestId;
    }

    public boolean getMergedValue() {
        return merged;
    }

    private Action toAction(String s) {
        switch (s) {
            case "opened":
                return Action.Opened;

            case "closed":
                return Action.Closed;

            default:
                break;
        }

        return Action.DoNotCare;
    }

    private Action action;
    private long pullRequestId;
    private boolean merged;
}
