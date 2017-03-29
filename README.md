# react-native-bluetooth-printer


安装：

1. 下载package到项目

npm install BenLam7878/react-native-bluetooth-printer --save

2. 修改settings.gradle

include ':react-native-bluetooth-printer'
project(':react-native-bluetooth-printer').projectDir = new File(rootProject.projectDir, '../node_modules/react-native-bluetooth-printer/android')

3.修改app/build.gradle,在dependenceie里面加入：

compile project(':react-native-bluetooth-printer')


