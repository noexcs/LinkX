package com.noexcs.indolent.agent.tools.termux

import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter

class TermuxExecuteCommandTool(private val executor: TermuxExecutor) : AgentTool {
    override val name = "execute_command"
    override val description = """
        Execute a shell command in Termux.

        You have full, unrestricted access to these termux commands and common shell commands.
        1. Decide to use tools on your own — no need to ask user.
        2. Chain multiple tools freely.
        3. On error, retry or try alternatives.
        4. Never tell users which tools have been used.
        5. Prefer tool-based answers over pure text.
        6. The user cannot see any tool output results at all，it's unnecessary to explain the output of the command to the user.

        Commands specific to the Termux environment, prioritizing the use of these commands for unrestricted and flexible interaction with users as much as possible:
        - termux-am
        - termux-am-socket
        - termux-api-start
        - termux-api-stop
        - termux-apps-info-app-version-name
        - termux-apps-info-app-version-name.bash
        - termux-apps-info-app-version-name.sh
        - termux-apps-info-env-variable
        - termux-apps-info-env-variable.bash
        - termux-apps-info-env-variable.sh
        - termux-audio-info
        - termux-backup
        - termux-battery-status
        - termux-brightness
        - termux-call-log
        - termux-camera-info
        - termux-camera-photo
          Usage: termux-camera-photo [-c camera-id] output-file
          Take a photo and save it to a file in JPEG format.
            -c camera-id  ID of the camera to use (see termux-camera-info), default: 0
        - termux-change-repo
        - termux-chroot
        - termux-clipboard-get
        - termux-clipboard-set
        - termux-contact-list
        - termux-dialog
        - termux-download
          Usage: termux-download [-d description] [-t title] url-to-download
          Download a resource using the system download manager.
            -d description  description for the download request notification
            -t title        title for the download request notification
            -p path         full path to which the file should be downloaded
        - termux-exec-ld-preload-lib
        - termux-exec-system-linker-exec
        - termux-fingerprint
          Usage: termux-fingerprint [-t title] [-d description] [-s subtitle] [-c cancel]
          Use fingerprint sensor on device to check for authentication
        - termux-fix-shebang
        - termux-info
        - termux-infrared-frequencies
        - termux-infrared-transmit
        - termux-job-scheduler
        - termux-keystore
        - termux-location
          Get the device location.
          -p provider  location provider [gps/network/passive] (default: gps)
          -r request   kind of request to make [once/last/updates] (default: once)
        - termux-media-player
          Usage: termux-media-player cmd [args]
          help        Shows this help
          info        Displays current playback information
          play        Resumes playback if paused
          play <file> Plays specified media file
          pause       Pauses playback
          stop        Quits playback
        - termux-media-scan
        - termux-microphone-record
        - termux-nfc
        - termux-notification
          Usage: termux-notification [options]
          Display a system notification. Content text is specified using -c/--content or read from stdin.
          Please read --help-actions for help with action arguments.
            --action action          action to execute when pressing the notification
            --alert-once             do not alert when the notification is edited
            --button1 text           text to show on the first notification button
            --button1-action action  action to execute on the first notification button
            --button2 text           text to show on the second notification button
            --button2-action action  action to execute on the second notification button
            --button3 text           text to show on the third notification button
            --button3-action action  action to execute on the third notification button
            -c/--content content     content to show in the notification. Will take
                                     precedence over stdin. If content is not passed as
                                     an argument or with stdin, then there will be a 3s delay.
            --channel channel-id     Specifies the notification channel id this notification should be send on.
                                     On Android versions lower than 8.0 this is a no-op.
                                     Create custom channels with termux-notification-channel.
                                     If the channel id is invalid, the notification will not be send.
            --group group            notification group (notifications with the same
                                     group are shown together)
          -h/--help                show this help
            --help-actions           show the help for actions
            -i/--id id               notification id (will overwrite any previous notification
                                     with the same id)
            --icon icon-name         set the icon that shows up in the status bar. View
                                     available icons at https://material.io/resources/icons/
                                     (default icon: event_note)
            --image-path path        absolute path to an image which will be shown in the
                                     notification
            --led-color rrggbb       color of the blinking led as RRGGBB (default: none)
            --led-off milliseconds   number of milliseconds for the LED to be off while
                                     it's flashing (default: 800)
            --led-on milliseconds    number of milliseconds for the LED to be on while
                                     it's flashing (default: 800)
            --on-delete action       action to execute when the the notification is cleared
            --ongoing                pin the notification
            --priority prio          notification priority (high/low/max/min/default)
            --sound                  play a sound with the notification
            -t/--title title         notification title to show
            --vibrate pattern        vibrate pattern, comma separated as in 500,1000,200
            --type type              notification style to use (default/media)
          Media actions (available with --type "media"):
            --media-next             action to execute on the media-next button
            --media-pause            action to execute on the media-pause button
            --media-play             action to execute on the media-play button
            --media-previous         action to execute on the media-previous button
        - termux-notification-channel
        - termux-notification-list
        - termux-notification-remove
        - termux-open
          Usage: termux-open [options] path-or-url
          Open a file or URL in an external app.
            --send               if the file should be shared for sending
            --view               if the file should be shared for viewing (default)
            --chooser            if an app chooser should always be shown
            --content-type type  specify the content type to use
        - termux-open-url
          usage: termux-open-url <url> [app_package_or_component]
          Open an URL for viewing.
        - termux-reload-settings
        - termux-reset
        - termux-restore
        - termux-saf-create
        - termux-saf-dirs
        - termux-saf-ls
        - termux-saf-read
        - termux-saf-rm
        - termux-saf-stat
        - termux-saf-write
        - termux-scoped-env-variable
        - termux-scoped-env-variable.bash
        - termux-scoped-env-variable.sh
        - termux-sensor
          Usage: termux-sensor
          Get information about types of sensors as well as live data
            -h, help           Show this help
            -a, all            Listen to all sensors (WARNING! may have battery impact)
            -c, cleanup        Perform cleanup (release sensor resources)
            -l, list           Show list of available sensors
            -s, sensors [,,,]  Sensors to listen to (can contain just partial name)
            -d, delay [ms]     Delay time in milliseconds before receiving new sensor update
            -n, limit [num]    Number of times to read sensor(s) (default: continuous) (min: 1)
        - termux-setup-package-manager
        - termux-setup-storage
        - termux-share
           Usage: termux-share [-a action] [-c content-type] [-d] [-t title] [file]
           Share a file specified as argument or the text received on stdin if no file argument is given.
             -a action        which action to performed on the shared content:
                                edit/send/view (default:view)
             -c content-type  content-type to use (default: guessed from file extension,
                                text/plain for stdin)
             -d               share to the default receiver if one is selected
                                instead of showing a chooser
             -t title         title to use for shared content (default: shared file name)
        - termux-sms-inbox
        - termux-sms-list
        - termux-sms-send
        - termux-speech-to-text
        - termux-storage-get
        - termux-telephony-call
        - termux-telephony-cellinfo
        - termux-telephony-deviceinfo
        - termux-toast
          Usage: termux-toast [-b bgcolor] [-c color] [-g gravity] [-s] [text]
          Show text in a Toast (a transient popup).
          The toast text is either supplied as arguments or read from stdin if no arguments are given. Arguments will take precedence over stdin.
          If toast text is not passed as arguments or with stdin, then there will be a 3s delay.
           -h  show this help
           -b  set background color (default: gray)
           -c  set text color (default: white)
           -g  set position of toast: [top, middle, or bottom] (default: middle)
           -s  only show the toast for a short while
          NOTE: color can be a standard name (i.e. red) or 6 / 8 digit hex value (i.e. "#FF0000" or "#FFFF0000") where order is (AA)RRGGBB. Invalid color will revert to default value
        - termux-torch
          Usage: termux-torch [on | off]
          Toggle LED Torch on devic
        - termux-tts-engines
        - termux-tts-speak
        - termux-usb
        - termux-vibrate
          Usage: termux-vibrate [-d duration] [-f]
          Vibrate the device.
            -d duration  the duration to vibrate in ms (default:1000)
            -f           force vibration even in silent mode
        - termux-volume
          Usage: termux-volume stream volume
          Change volume of audio stream
          Valid audio streams are: alarm, music, notification, ring, system, call
          Call w/o arguments to show information about each audio stream
        - termux-wake-lock
        - termux-wake-unlock
        - termux-wallpaper
          Change wallpaper on your device
          Usage: termux-wallpaper [options]
          -h         show this help
          -f <file>  set wallpaper from file
          -u <url>   set wallpaper from url resource
          -l         set wallpaper for lockscreen (Nougat and later)
        - termux-wifi-connectioninfo
        - termux-wifi-enable
          Usage: termux-wifi-enable [true | false]
          Toggle Wi-Fi On/Off
        - termux-wifi-scaninfo
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "command",
            type = "string",
            description = "The shell command to execute"
        ),
        ToolParameter(
            name = "workdir",
            type = "string",
            description = "Working directory for the command (default: /data/data/com.termux/files/home)",
            required = false
        )
    )

    override suspend fun execute(args: Map<String, Any?>): String {
        val command = args["command"] as? String ?: ""
        val workdir = args["workdir"] as? String ?: "/data/data/com.termux/files/home"

        val result = executor.execute(command, workdir)
        return buildString {
            if (result.stdout.isNotEmpty()) appendLine(result.stdout)
            if (result.stderr.isNotEmpty()) appendLine("STDERR: ${result.stderr}")
            appendLine("Exit code: ${result.exitCode}")
        }.trim()
    }
}
