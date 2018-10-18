#!/usr/bin/env groovy
import groovy.util.CliBuilder;
import groovy.json.*;
jsonSlurper = new JsonSlurper();
import java.text.*;

def exec(cmd){
//    println cmd
    def sout = new StringBuilder(), serr = new StringBuilder();
    def proc = cmd.execute();
    proc.consumeProcessOutput(sout, serr);
    proc.waitForOrKill(5000);

    return "$sout";
}

def curlApi(url, token){
//    println "curl $url";
    def header = "Access-Token: ${token}";
    def cmd = ['curl', '--header', header, '--data-urlencode', 'active="true"', '--get', url];
    return exec(cmd);
}

def getUserName(token){
    def name_url = "https://api.pushbullet.com/v2/users/me";
    def user = jsonSlurper.parseText(curlApi(name_url, token));
    return user.email_normalized.split("@")[0];
}

def write2File(content, path){
    def file = new File(path);
    println "write $path";
    file.write(content);
}

def parseArgs(args){
    def cli = new CliBuilder(usage: 'pushbullet-backup.groovy -[htci]');

    cli.with {
        h longOpt: 'help', 'Show usage information'
        t longOpt: 'api-token', args: 1, argName: 'api_token', 'Your Pushbullet api token.'
        c longOpt: 'cursor', args: 1, argName: 'cursor', 'Starting cursor.'
        i longOpt: 'index', args: 1, argName: 'index', 'Starting index for file name.'
    }

    def options = cli.parse(args);
    if(!options)
        return;

    if(!options.t || options.h || (options.c && !options.i) || (options.i && !options.c)){
        cli.usage();
        return;
    }

    api_token = options.t;
    if(options.c)
        cursor = options.c;
    if(options.i)
        start_index = options.i;
}

def main(){
    def username = getUserName(api_token);

    def backup_folder = exec("pwd").replace("\n", "");
    def msg_url = "https://api.pushbullet.com/v2/pushes?limit=100";
    
    def url;
    def i = start_index;

    while(true) {
        url = msg_url;
        if(cursor != null)
            url += "&cursor=$cursor";

        def path = "${backup_folder}/${username}-backup-${i}.json";

        def content_text = JsonOutput.prettyPrint(curlApi(url, api_token));
        write2File(content_text, path);

        def content = jsonSlurper.parseText(content_text);

        cursor = content.cursor;
        if(cursor == null)
            break;
        i++;
    }
}

api_token = null;
cursor = null;
start_index = 1;
parseArgs(args);

if(api_token)
    main();