package com.baijiahulian.live.ui.rightbotmenu;

import com.baijiahulian.live.ui.activity.LiveRoomRouterListener;
import com.baijiahulian.live.ui.utils.RxUtils;
import com.baijiahulian.livecore.utils.LPErrorPrintSubscriber;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by Shubo on 2017/2/16.
 */

public class RightBottomMenuPresenter implements RightBottomMenuContract.Presenter {

    private RightBottomMenuContract.View view;

    private LiveRoomRouterListener liveRoomRouterListener;

    private Subscription subscriptionOfCamera, subscriptionOfMic, subscriptionOfVolume;

    public RightBottomMenuPresenter(RightBottomMenuContract.View view) {
        this.view = view;
    }

    @Override
    public void changeZoom() {
        if (liveRoomRouterListener.getCurrentScreenOrientation() == 2) {
            view.showZoomIn();
        } else if (liveRoomRouterListener.getCurrentScreenOrientation() == 1) {
            view.showZoomOut();
        }
        liveRoomRouterListener.changeScreenOrientation();
    }

    @Override
    public void changeAudio() {
        if (liveRoomRouterListener.getLiveRoom().getRecorder().isAudioAttached()) {
            liveRoomRouterListener.getLiveRoom().getRecorder().detachAudio();
            if (!liveRoomRouterListener.getLiveRoom().getRecorder().isVideoAttached()) {
                liveRoomRouterListener.getLiveRoom().getRecorder().stopPublishing();
            }
        } else {
            if (!liveRoomRouterListener.getLiveRoom().getRecorder().isPublishing()) {
                liveRoomRouterListener.getLiveRoom().getRecorder().publish();
            }
            liveRoomRouterListener.getLiveRoom().getRecorder().attachAudio();
        }
    }

    @Override
    public void changeVideo() {
        if (liveRoomRouterListener.getLiveRoom().getRecorder().isVideoAttached()) {
            liveRoomRouterListener.detachVideo();
            if (!liveRoomRouterListener.getLiveRoom().getRecorder().isAudioAttached()) {
                liveRoomRouterListener.getLiveRoom().getRecorder().stopPublishing();
            }
        } else {
            if (!liveRoomRouterListener.getLiveRoom().getRecorder().isPublishing()) {
                liveRoomRouterListener.getLiveRoom().getRecorder().publish();
            }
            liveRoomRouterListener.attachVideo();
        }
    }

    @Override
    public void more(int anchorX, int anchorY) {
        liveRoomRouterListener.showMorePanel(anchorX, anchorY);
    }

    @Override
    public void getSysRotationSetting() {
        /*避免引入sdk代码，这里直接用了数字*/
        if (liveRoomRouterListener.getSysRotationSetting() == 1) {
            //可自由转屏
            view.hideZoom();
        } else if (liveRoomRouterListener.getSysRotationSetting() == 0) {
            //不可自由转屏
            view.showZoom();
            //横屏
            if (liveRoomRouterListener.getCurrentScreenOrientation() == 2) {
                view.showZoomOut();
            } else if (liveRoomRouterListener.getCurrentScreenOrientation() == 1) {
                //竖屏
                view.showZoomIn();
            }
        }
    }

    @Override
    public void setSysRotationSetting() {
        if (liveRoomRouterListener.getSysRotationSetting() == 1) {
            //可自由转屏
            view.hideZoom();
            liveRoomRouterListener.letScreenRotateItself();
        } else if (liveRoomRouterListener.getSysRotationSetting() == 0) {
            //不可自由转屏
            view.showZoom();
            liveRoomRouterListener.forbidScreenRotateItself();
            //横屏
            if (liveRoomRouterListener.getCurrentScreenOrientation() == 2) {
                view.showZoomOut();
            } else if (liveRoomRouterListener.getCurrentScreenOrientation() == 1) {
                //竖屏
                view.showZoomIn();
            }
        }

    }

    @Override
    public void setRouter(LiveRoomRouterListener liveRoomRouterListener) {
        this.liveRoomRouterListener = liveRoomRouterListener;
    }

    @Override
    public void subscribe() {
        subscriptionOfCamera = liveRoomRouterListener.getLiveRoom().getRecorder().getObservableOfCameraOn()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new LPErrorPrintSubscriber<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        view.showVideoStatus(aBoolean);
                    }
                });
        subscriptionOfMic = liveRoomRouterListener.getLiveRoom().getRecorder().getObservableOfMicOn()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new LPErrorPrintSubscriber<Boolean>() {
                    @Override
                    public void call(Boolean aBoolean) {
                        view.showAudioStatus(aBoolean);
                    }
                });
        subscriptionOfVolume = liveRoomRouterListener.getLiveRoom().getRecorder().getObservableOfVolume()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new LPErrorPrintSubscriber<Integer>() {
                    @Override
                    public void call(Integer integer) {
                        if (liveRoomRouterListener.getLiveRoom().getRecorder().isAudioAttached())
                            view.showVolume(integer);
                    }
                });
        if (!liveRoomRouterListener.isTeacherOrAssistant()) {
            liveRoomRouterListener.disableSpeakerMode();
        }
        getSysRotationSetting();
        //横屏
        if (liveRoomRouterListener.getCurrentScreenOrientation() == 2) {
            view.showZoomOut();
        } else if (liveRoomRouterListener.getCurrentScreenOrientation() == 1) {
            //竖屏
            view.showZoomIn();
        }
    }

    @Override
    public void unSubscribe() {
        RxUtils.unSubscribe(subscriptionOfCamera);
        RxUtils.unSubscribe(subscriptionOfMic);
        RxUtils.unSubscribe(subscriptionOfVolume);
    }

    @Override
    public void destroy() {
        liveRoomRouterListener = null;
        view = null;
    }
}
