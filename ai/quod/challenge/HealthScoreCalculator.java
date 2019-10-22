package ai.quod.challenge;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.io.*;
import java.util.zip.GZIPInputStream;
import java.net.URL; 
import java.net.MalformedURLException;
import java.text.DecimalFormat;
import org.json.JSONException;
 
class Calculator {
    Calculator(ZonedDateTime _from, ZonedDateTime _to) {
        this.from = _from;
        this.to   = _to;
        repoIDtoHealthScoreMap = new HashMap<Long, RepoHealthScoreCalculator>();
    }
 
    public void consumePushEvent(Event e, PushEventPayLoad payload) {
        if (!isInTimeRange(e.getZonedDateTime()))
            return;
        Repo repo = e.getRepo();
        RepoHealthScoreCalculator repoHealthScore = findRepoHealthScoreCalculator(repo); 
        repoHealthScore.consumePushEvent(e, payload);
    }

    public void consumeIssuesEvent(Event e, IssuesEventPayLoad payload) {
        if (!isInTimeRange(e.getZonedDateTime()))
            return;
        findRepoHealthScoreCalculator(e.getRepo()).consumeIssuesEvent(e, payload); 
    }

    public void consumePullRequestEvent(Event e, PullRequestEventPayLoad payload) {
        if (!isInTimeRange(e.getZonedDateTime()))
            return;
        findRepoHealthScoreCalculator(e.getRepo()).consumePullRequestEvent(e, payload); 
    }

    public void dumpResultToCSV(String filename) throws IOException{
        TreeMap<Float, RepoHealthScoreCalculator> sortedMap = new TreeMap<Float, RepoHealthScoreCalculator>(java.util.Collections.reverseOrder());
        int maxCommitCounter = 0;
        float maxNumberOfCommitPerDeveloper = 0; 
        float minAverageIssueOpenTime = Float.MAX_VALUE;
        float minAverageMergeTime = Float.MAX_VALUE;

        for (RepoHealthScoreCalculator r : repoIDtoHealthScoreMap.values()) {
            int commitCounter = r.getNumberOfCommitPerDayScoreCalculator().getCommitCounter();
            float numberOfCommitPerDeveloper = r.getNumberOfCommitPerDeveloperScoreCalculator().getNumberOfCommitPerDeveloper();
            float averageIssueOpenTime = r.getAverageIssueOpenTimeScoreCalculator().getAverageOpenTime();
            float averageMergeTime = r.getPullRequestAverageMergeTimeScoreCalculator().getAverageMergeTime();

            if (commitCounter > maxCommitCounter) {
                maxCommitCounter = commitCounter;
            }

            if (numberOfCommitPerDeveloper > maxNumberOfCommitPerDeveloper) {
                maxNumberOfCommitPerDeveloper = numberOfCommitPerDeveloper;
            }

            if (averageIssueOpenTime < minAverageIssueOpenTime) {
                minAverageIssueOpenTime = averageIssueOpenTime;
            }

            if (averageMergeTime < minAverageMergeTime) {
                minAverageMergeTime = averageMergeTime;
            }
        }

        for (RepoHealthScoreCalculator r : repoIDtoHealthScoreMap.values()) {
            sortedMap.put(r.calculateScore(maxCommitCounter, maxNumberOfCommitPerDeveloper, minAverageIssueOpenTime, minAverageMergeTime), r);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filename, true))) {
            writer.write(RepoHealthScoreCalculator.CSVHeader() + '\n');
            for (RepoHealthScoreCalculator r : sortedMap.values()) {
                writer.write(r.toCSVData() + '\n');
            }
            writer.close();
        }
    }

    private boolean isInTimeRange(ZonedDateTime t) {
        if (t.compareTo(to) <= 0 && t.compareTo(from) >= 0) {
            return true;
        }

        return false;
    }

    private RepoHealthScoreCalculator findRepoHealthScoreCalculator(Repo repo) {
        long repoId = repo.getId();
        if (!repoIDtoHealthScoreMap.containsKey(repoId)) {
            repoIDtoHealthScoreMap.put(repoId, new RepoHealthScoreCalculator(repo, from, to));
        }

        return repoIDtoHealthScoreMap.get(repoId);
    }

    private ZonedDateTime from;
    private ZonedDateTime to;
    private HashMap<Long, RepoHealthScoreCalculator> repoIDtoHealthScoreMap;
}

public class HealthScoreCalculator {
	public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Example: java ai.quod.challenge.HealthScoreCalculator 2019-08-01T00:00:00Z 2019-09-01T00:00:00Z");
            return; 
        }

        ZonedDateTime from = ZonedDateTime.parse(args[0]);
        ZonedDateTime to = ZonedDateTime.parse(args[1]);

        if (from.compareTo(to) >= 0) {
            System.out.println("start time must smaller than end time!");
            return;
        }

        Calculator calculator = new Calculator(from, to);
        ArrayList<URL> urls = makeResourceURLs(from, to);

        for (URL u : urls) {
            System.out.println(u);
        }
        
        for (URL url : urls) {
            try (GZIPInputStream in = new GZIPInputStream(url.openStream())){
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                while(reader.ready()) {
                    String line = reader.readLine();
                    try {
                        Event event = new Event(line);
                        switch(event.getType()) {
                            case PushEvent:
                                PushEventPayLoad pushEventPayLoad = new PushEventPayLoad(line);
                                calculator.consumePushEvent(event, pushEventPayLoad);
                                break;
                            case IssuesEvent:
                                IssuesEventPayLoad issuesEventPayLoad = new IssuesEventPayLoad(line);
                                calculator.consumeIssuesEvent(event, issuesEventPayLoad);
                                break;
                            case PullRequestEvent:
                                PullRequestEventPayLoad pullRequestEventPayLoad = new PullRequestEventPayLoad(line);
                                break;
                            default:
                                break;
                        }
                    } catch (JSONException e) {
                        System.out.println("Fail to parse json, input:\n" + line + "\ngot exception:" + e);
                        continue;
                    }
                }
            }
        }
        calculator.dumpResultToCSV("health_scores.csv");
	}

    private static URL makeResourceURL(ZonedDateTime time) throws MalformedURLException {
        int year = time.getYear();
        int month = time.getMonthValue();
        int day = time.getDayOfMonth();
        int hour = time.getHour();
        return new URL("https://data.gharchive.org/" + year + '-' + new DecimalFormat("00").format(month) + '-' + new DecimalFormat("00").format(day) + '-' + hour + ".json.gz");
    }

    private static ArrayList<URL> makeResourceURLs(ZonedDateTime begin, ZonedDateTime end) throws MalformedURLException {
        ArrayList<URL> urls = new ArrayList<URL>();
        ZonedDateTime iterateTime = begin.withMinute(0).withSecond(0);
        while (iterateTime.compareTo(end) < 0) {
            URL url = makeResourceURL(iterateTime);
            urls.add(url);
            iterateTime = iterateTime.plusHours(1);  
        } 

        return urls;
    }


    
}

