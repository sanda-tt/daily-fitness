$adb = "D:\developer\SDK\platform-tools\adb.exe"
$dir = "D:\developer\androidstudio\.design\gym\推文素材\screenshots"

function Shot($name) {
    & $adb shell screencap -p /sdcard/_shot.png | Out-Null
    & $adb pull /sdcard/_shot.png (Join-Path $dir $name) | Out-Null
    & $adb shell rm /sdcard/_shot.png | Out-Null
    Write-Host "captured $name"
}

# 01 清数据后的纯净今日页
& $adb shell pm clear com.example.gym | Out-Null
Start-Sleep -Seconds 1
& $adb shell am start -n com.example.gym/.MainActivity | Out-Null
Start-Sleep -Seconds 3
Shot "01-today-fresh.png"

# 写入构造好的训练数据
& $adb shell am force-stop com.example.gym
& $adb shell "run-as com.example.gym cp /data/local/tmp/gym_prefs.xml shared_prefs/gym_prefs.xml"
& $adb shell am start -n com.example.gym/.MainActivity | Out-Null
Start-Sleep -Seconds 3
Shot "02-today-weights.png"

# 03 设置页（默认展开当天 周二）
& $adb shell input tap 810 2270
Start-Sleep -Seconds 2
Shot "03-settings-tue.png"

# 04 重量记录中心
& $adb shell input tap 540 430
Start-Sleep -Seconds 2
Shot "04-records-hub.png"

# 05 硬拉折线图
& $adb shell input tap 540 560
Start-Sleep -Seconds 2
Shot "05-chart-deadlift.png"

# 06 下滑看历史列表
& $adb shell input swipe 540 1800 540 900 300
Start-Sleep -Seconds 1
Shot "06-history-list.png"

# 回到按钮位置，07 记录新重量弹窗
& $adb shell input swipe 540 800 540 1800 300
Start-Sleep -Seconds 1
& $adb shell input tap 577 1250
Start-Sleep -Seconds 1
Shot "07-add-weight-dialog.png"

# 连退三次回到设置
& $adb shell input keyevent KEYCODE_BACK; Start-Sleep -Milliseconds 600
& $adb shell input keyevent KEYCODE_BACK; Start-Sleep -Milliseconds 600
& $adb shell input keyevent KEYCODE_BACK; Start-Sleep -Seconds 2

# 08 编辑项目弹窗
& $adb shell input tap 540 770
Start-Sleep -Seconds 1
Shot "08-edit-exercise-dialog.png"
Write-Host "all done"
