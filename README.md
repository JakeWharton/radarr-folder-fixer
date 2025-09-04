# Radarr Folder Fixer

The title and year of a movie changes over time, and Radarr does not update that folder automatically.
This tool will tag movies whose folder name is out-of-date allowing you to update it.


## Installation

### Docker

The binary is available inside the `jakewharton/radarr-folder-fixer` Docker container.

[![Docker Image Version](https://img.shields.io/docker/v/jakewharton/radarr-folder-fixer?sort=semver&style=flat-square)][hub]
[![Docker Image Size](https://img.shields.io/docker/image-size/jakewharton/radarr-folder-fixer?sort=semver&style=flat-square)][hub]<br>
[![Docker Image Version](https://img.shields.io/docker/v/jakewharton/radarr-folder-fixer/trunk?style=flat-square)][hub]
[![Docker Image Size](https://img.shields.io/docker/image-size/jakewharton/radarr-folder-fixer/trunk?style=flat-square)][hub]

 [hub]: https://hub.docker.com/r/jakewharton/radarr-folder-fixer/

By default, the tool will run a single sync and then exit.

```
$ docker run -d \
    jakewharton/radarr-folder-fixer \
      --api-key abc123 \
      --host https://radarr.example.com \
```

If you specify the `--cron` option with a valid cron specifier, the tool will not exit and perform automatic syncs in accordance with the schedule.
For help creating a valid cron specifier, visit [cron.help](https://cron.help/#0_*_*_*_*).

To be notified when sync is failing visit https://healthchecks.io, create a check, and specify the ID to the container using the `--hc-id` option.
You can also specify a custom host with `--hc-host`.

If you're using Docker Compose, all the options are available as environment variables.

```yaml
services:
  gitout:
    image: jakewharton/radarr-folder-fixer:latest
    restart: unless-stopped
    environment:
      - "RADARR_FOLDER_FIXER_API_KEY=abc123"
      - "RADARR_FOLDER_FIXER_HOST=https://radarr.example.com"
      - "RADARR_FOLDER_FIXER_CRON=0 * * * *"
      #Optional:
      - "RADARR_FOLDER_FIXER_HC_ID=..."
      - "RADARR_FOLDER_FIXER_HC_HOTS=..."
```

Note: You may want to specify an explicit version rather than `latest`.
See https://hub.docker.com/r/jakewharton/radarr-folder-fixer/tags or `CHANGELOG.md` for the available versions.
Use `trunk` for the latest changes.

### Binaries

A `.zip` can be downloaded from the [latest GitHub release](https://github.com/JakeWharton/radarr-folder-fixer/releases/latest).

The Java Virtual Machine must be installed on your system to run.
After unzipping, run either `bin/radarr-folder-fixer` (macOS, Linux) or `bin/radarr-folder-fixer.bat` (Windows).


## Usage

```
$ radarr-folder-fixer --help
Usage: radarr-folder-fixer [<options>]

Options:
  --host=<value>       Radarr host
  --api-key=<text>     Radarr API key
  --tag=<text>         Tag to add to movies with mismatched folders
  --ignore-tag=<text>  Tag indicating the tool should ignore folder mismatches
  --dry-run            Print actions instead of performing them
  --cron=<expression>  Run command forever and perform sync on this schedule
  --hc-id=<id>         ID of Healthchecks.io service to notify
  --hc-host=<url>      Host of Healthchecks.io service to notify. Requires --hc-id
  -v, --verbose        Increase logging verbosity. -v = informational, -vv = debug, -vvv = trace
  -h, --help           Show this message and exit
```


# LICENSE

    Copyright 2025 Jake Wharton

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
