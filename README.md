# quod-ai-challenge

### Dependency 
- depend on package org.json-chargebee-1.0.jar that is already included locally

### How to setup and build
- Step 1: clone the source code, cd to root folder
- Step 2: build by execute command: `javac -cp org.json-chargebee-1.0.jar ai/quod/challenge/*.java`, or in linux you can run `. build.sh` 

### How to run
- Step 1: Change CLASSPATH variable so that it include path to json package, in linux you can execute command `export CLASSPATH=./org.json-chargebee-1.0.jar:$CLASSPATH` 
- Step 2: Run with command like the following: `java ai.quod.challenge.HealthScoreCalculator 2019-08-01T01:00:00Z 2019-08-01T02:00:00Z`
If you in linux, you can run those two steps above with `. run.sh` 

### Technical decisions
- I use a Json package so I could parse json data, I believe this is the most popular package.
- If I have more time, I would have:
    + Add test for this code. 
    + Improve algorithm to calculate metrics, this improvement will not make any differences to code structure, just local inside some class. 
    + Add check to constructors. 
    + Rename some method from having `get` prefix to `calculate` prefix since it modify state of class. 
    + Create a separate file for each class instead of a lot of class shared a single file. 
    + Find new way of download a url since the current way failed to download in some network condition.
    + Improve exception handling. I do not familiar with style of throwing exception everywhere in Java.

