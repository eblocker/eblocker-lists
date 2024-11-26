# Tools

## mails2json.py

Extracts app suggestions sent in by users via email.

Workflow:

* Open macOS Mail
* Go to folder "20 Ready for JSON"
* Select all emails in the folder with `[command]-A`
* Click `File / Save As...` and select format `Plain Text`
* Enter a file name (e.g. `mails.txt`) and click `Save`

Now you can extract app suggestions in JSON format from the text file
with the command:

    ./mails2json.py mails.txt > suggestions.json

Please note:

* Existing apps have an ID set (extracted from the email subject)
* New apps have their ID set to `-1`.
* There might still be apps that have an already existing name. They
  must be merged manually.
