# nestedRecycler
Android 吸顶嵌套滑动

1. #### Add it in your root build.gradle at the end of repositories:

```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

```groovy
dependencies {
    implementation 'com.github.bojianyin:nestedRecycler:1.2.0'
}
```

2. #### 初始化ParentRecyclerView
```kotlin
ParentRecyclerView.initLayoutManager()
```
3. #### 设置ParentRecyclerView的adapter继承BaseMultiTypeAdapter且实现getCurrentChildRecyclerView方法