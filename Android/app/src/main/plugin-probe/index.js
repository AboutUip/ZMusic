// 自定义空间集
const customSpace = new Object();

// 初始化
function init(){
    Xuan.runtime.register(Xuan.runtime.State.Initializing);
    customSpace["ZMusicVersion"] = Xuan.zmusic.version;
    customSpace["ZMusicVersionNumber"] = Xuan.zmusic.versionNumber;
    customSpace["EngineVersion"] = Xuan.engine.version;
    customSpace["EngineVersionNumber"] = Xuan.engine.versionNumber;
    return main();
}

// 主函数
function main(){
    Xuan.runtime.register(Xuan.runtime.State.Running);
    try{
        Xuan.delay(5000);
        Xuan.notice.show(`ZMusicVersion: ${customSpace.ZMusicVersion}`);
        Xuan.delay(2000);
        Xuan.notice.show(`ZMusicVersionNumber: ${customSpace.ZMusicVersionNumber}`);
        Xuan.delay(2000);
        Xuan.notice.show(`EngineVersion: ${customSpace.EngineVersion}`);
        Xuan.delay(2000);
        Xuan.notice.show(`EngineVersionNumber: ${customSpace.EngineVersionNumber}`);
    }catch(error){
        // do something
        Xuan.runtime.register(Xuan.runtime.State.Error);
        return false;
    }
    return true;
}

// 运行初始化
init();
