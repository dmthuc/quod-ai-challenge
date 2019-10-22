package ai.quod.challenge;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.io.*;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.net.URL; 
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.lang.Exception;
import java.time.temporal.ChronoUnit;
 
class RepoHealthScoreCalculator {
    public RepoHealthScoreCalculator(Repo _repo, ZonedDateTime from, ZonedDateTime to) {
        repo = _repo;
        numberOfCommitPerDayScoreCalculator = new NumberOfCommitPerDayScoreCalculator(from, to);
        numberOfCommitPerDeveloperScoreCalculator = new NumberOfCommitPerDeveloperScoreCalculator();
        averageIssueOpenTimeScoreCalculator = new AverageIssueOpenTimeScoreCalculator(from, to);
        pullRequestAverageMergeTimeScoreCalculator = new PullRequestAverageMergeTimeScoreCalculator(from, to);
    }

    public void consumePushEvent(Event event, PushEventPayLoad payload) {
        numberOfCommitPerDayScoreCalculator.consumePushEvent(event, payload);
        numberOfCommitPerDeveloperScoreCalculator.consumePushEvent(event, payload);
    }
    
    public void consumeIssuesEvent(Event event, IssuesEventPayLoad payload) {
        averageIssueOpenTimeScoreCalculator.consumeIssuesEvent(event, payload);
    }

    public void consumePullRequestEvent(Event event, PullRequestEventPayLoad payload) {

    }

    public NumberOfCommitPerDayScoreCalculator getNumberOfCommitPerDayScoreCalculator() {
        return numberOfCommitPerDayScoreCalculator;
    } 

    public NumberOfCommitPerDeveloperScoreCalculator getNumberOfCommitPerDeveloperScoreCalculator() {
        return numberOfCommitPerDeveloperScoreCalculator; 
    }

    public AverageIssueOpenTimeScoreCalculator getAverageIssueOpenTimeScoreCalculator() {
        return averageIssueOpenTimeScoreCalculator;
    }

    public PullRequestAverageMergeTimeScoreCalculator getPullRequestAverageMergeTimeScoreCalculator() {
        return pullRequestAverageMergeTimeScoreCalculator;
    }
    
    public float calculateScore(int maxCommitCounter, float maxNumberOfCommitPerDeveloper, float minAverageIssueOpenTime, float minAverageMergeTime) {
        score = numberOfCommitPerDayScoreCalculator.getScore(maxCommitCounter) +
                numberOfCommitPerDeveloperScoreCalculator.getScore(maxNumberOfCommitPerDeveloper) + 
                averageIssueOpenTimeScoreCalculator.getScore(minAverageIssueOpenTime); 
                pullRequestAverageMergeTimeScoreCalculator.getScore(minAverageMergeTime);

        return score;
    }

    public static String CSVHeader() {
        return "org,repo_name,health_score,num_commits"; 
    }
    
    public String toCSVData() {
        return escapeString(repo.getOrgName()) + ',' + escapeString(repo.getName()) + ',' + score + ',' + numberOfCommitPerDayScoreCalculator.getCommitCounter();
    }
    
    private Repo repo;
    private NumberOfCommitPerDayScoreCalculator numberOfCommitPerDayScoreCalculator;
    private NumberOfCommitPerDeveloperScoreCalculator numberOfCommitPerDeveloperScoreCalculator;
    private AverageIssueOpenTimeScoreCalculator averageIssueOpenTimeScoreCalculator;
    private PullRequestAverageMergeTimeScoreCalculator pullRequestAverageMergeTimeScoreCalculator;

    private float score;

	private static String escapeString(String str) {
        char QUOTE = '\"';
        char SEPARATOR = ','; 
		StringBuilder sb = new StringBuilder();
		sb.append(QUOTE);
		boolean mustBeQuoted = false;
		for (int i = 0; i < str.length(); i++) {
			char ch = str.charAt(i);
			if (ch == '\n' || ch == '\r') {
				ch = ' ';
			}
			sb.append(ch);
			if (ch == QUOTE) {
				sb.append(ch);
				mustBeQuoted = true;
			}
			if (ch == SEPARATOR) {
				mustBeQuoted = true;
			}
		}
		sb.append(QUOTE);
		return mustBeQuoted ? sb.toString() : sb.substring(1, sb.length() - 1);
	}
}

class NumberOfCommitPerDayScoreCalculator {
    public NumberOfCommitPerDayScoreCalculator(ZonedDateTime from, ZonedDateTime to) {
        numberOfDay = 0;
        
        ZonedDateTime iterateTime = from;
        while (iterateTime.compareTo(to) <= 0) {
            iterateTime = iterateTime.plusDays(1);  
            ++numberOfDay;
        } 
    }

    public void consumePushEvent(Event event, PushEventPayLoad payload) {
        commitCounter += payload.getNumberOfDistinctCommit();       
    }

    public int getCommitCounter() {
        return commitCounter;
    }

    public float getScore(int maxCommitCounter) {
        if (maxCommitCounter == 0) {
            return 0;
        }
        return (float) commitCounter/maxCommitCounter;
    }

    private int numberOfDay;
    private int commitCounter;
}

class NumberOfCommitPerDeveloperScoreCalculator {
    public NumberOfCommitPerDeveloperScoreCalculator() {
        actorIds = new TreeSet<Long>();
    }

    public void consumePushEvent(Event event, PushEventPayLoad payload) {
        commitCounter += payload.getNumberOfDistinctCommit();       
        actorIds.add(event.getActorId());
    }
    
    public float getNumberOfCommitPerDeveloper() {
        return (float) commitCounter/ actorIds.size();
    }

    public float getScore(float maxNumberOfCommitPerDeveloper) {
        if (maxNumberOfCommitPerDeveloper == 0) {
            return 0;
        }

        return (float) commitCounter/ (actorIds.size() * maxNumberOfCommitPerDeveloper);
    }

    private int commitCounter;
    private TreeSet<Long> actorIds;
}

class AverageIssueOpenTimeScoreCalculator {
    public AverageIssueOpenTimeScoreCalculator(ZonedDateTime _from, ZonedDateTime _to) {
        issueIdToOpenTimeCalculatorMap = new HashMap<Long, OpenTimeCalculator>();
        from = _from;
        to = _to;
    }

    public void consumeIssuesEvent(Event event, IssuesEventPayLoad payload) {
        long issueId = payload.getIssueId();
        findOpenTimeCalculator(issueId).consumeIssuesEvent(event, payload);
    }

    public OpenTimeCalculator findOpenTimeCalculator(long issueId) {
        if (!issueIdToOpenTimeCalculatorMap.containsKey(issueId)) {
            issueIdToOpenTimeCalculatorMap.put(issueId, new OpenTimeCalculator(from, to));
        }

        return issueIdToOpenTimeCalculatorMap.get(issueId);
    }

    public float getAverageOpenTime() {
        if (issueIdToOpenTimeCalculatorMap.size() == 0) {
            return ChronoUnit.SECONDS.between(from, to); 
        }

        long totalOpentime = 0;
        for (OpenTimeCalculator c : issueIdToOpenTimeCalculatorMap.values()) {
            totalOpentime += c.getOpenDuration();
        }
        return (float) totalOpentime / issueIdToOpenTimeCalculatorMap.size();
    } 

    public float getScore(float minAverageOpenTime) {
        float averageOpenTime = getAverageOpenTime();

        if (averageOpenTime == 0) {
            return 1;
        } else {
            return minAverageOpenTime / averageOpenTime;
        }
    }

    private HashMap<Long, OpenTimeCalculator> issueIdToOpenTimeCalculatorMap;
    private ZonedDateTime from;
    private ZonedDateTime to;
}

class OpenTimeCalculator {
    public OpenTimeCalculator(ZonedDateTime _from, ZonedDateTime _to) {
        lastOpenTime = _from;
        to = _to;
        isClosed = false;
    }

    public void consumeIssuesEvent(Event event, IssuesEventPayLoad payload) {
        if (payload.getAction() == IssuesEventPayLoad.Action.Opened || payload.getAction() == IssuesEventPayLoad.Action.Reopened) {
            lastOpenTime = event.getZonedDateTime(); 
            isClosed = false;
        } else if (payload.getAction() == IssuesEventPayLoad.Action.Closed) {
            openDuration += ChronoUnit.SECONDS.between(event.getZonedDateTime(), lastOpenTime);
            isClosed = true;
        }
    }

    public long getOpenDuration() {
        if (isClosed == false) {
            return openDuration += ChronoUnit.SECONDS.between(to, lastOpenTime);
        }

        return openDuration;
    }

    private long openDuration;
    private ZonedDateTime lastOpenTime;
    private boolean isClosed;
    private ZonedDateTime to;
}

class PullRequestAverageMergeTimeScoreCalculator {
    public PullRequestAverageMergeTimeScoreCalculator(ZonedDateTime _from, ZonedDateTime _to) {
        requestIdToMergeTimeCalculatorMap = new HashMap<Long, MergeTimeCalculator>();
        from = _from;
        to = _to;
    }

    public void consumePullRequestEvent(Event event, PullRequestEventPayLoad payload) {
        long pullRequestId = payload.getPullRequestId();
        findMergeTimeCalculator(pullRequestId).consumePullRequestEvent(event, payload);
    }

    public MergeTimeCalculator findMergeTimeCalculator(long pullRequestId) {
        if (!requestIdToMergeTimeCalculatorMap.containsKey(pullRequestId)) {
            requestIdToMergeTimeCalculatorMap.put(pullRequestId, new MergeTimeCalculator());
        }

        return requestIdToMergeTimeCalculatorMap.get(pullRequestId);
    }

    public float getAverageMergeTime() {
        long totalMergeTime = 0;
        int mergeCount = 0;
        for (MergeTimeCalculator c : requestIdToMergeTimeCalculatorMap.values()) {
            if (c.isMergeTimeValid() == true) {
                totalMergeTime += c.getMergeTime();
                ++mergeCount;
            }
        }

        if (mergeCount == 0) {
            return ChronoUnit.SECONDS.between(from, to); 
        }

        return (float) totalMergeTime / mergeCount;
    }

    public float getScore(float minAverageMergeTime) {
        float averageMergeTime = getAverageMergeTime();

        if (averageMergeTime == 0) {
            return 1;
        } else {
            return minAverageMergeTime / averageMergeTime;
        }
    }
    

    private HashMap<Long, MergeTimeCalculator> requestIdToMergeTimeCalculatorMap;

    private ZonedDateTime from;
    private ZonedDateTime to;
}

class MergeTimeCalculator {
    public MergeTimeCalculator() {
        hasRequestTime = false;
        hasValidMergeTime = false;
    }

    public long getMergeTime() {
        return mergeTime;
    }

    public void consumePullRequestEvent(Event event, PullRequestEventPayLoad payload) {
        switch(payload.getAction()) {
            case Opened:
                hasRequestTime = true;
                break;
            case Closed:
                if (hasRequestTime == true && payload.getMergedValue() == true) {
                    mergeTime = ChronoUnit.SECONDS.between(requestTime, event.getZonedDateTime());
                    hasValidMergeTime = true;                
                }
                break;
            default:
                break;
        }
    } 

    public boolean isMergeTimeValid() {
        return hasValidMergeTime;
    } 

    private ZonedDateTime requestTime;
    private boolean hasRequestTime;
    private long mergeTime;
    private boolean hasValidMergeTime; 
}
