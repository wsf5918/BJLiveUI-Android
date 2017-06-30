package com.baijiahulian.live.ui.speakerspanel;

import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.baijiahulian.live.ui.activity.LiveRoomRouterListener;
import com.baijiahulian.live.ui.ppt.MyPPTFragment;
import com.baijiahulian.live.ui.utils.RxUtils;
import com.baijiahulian.livecore.context.LPConstants;
import com.baijiahulian.livecore.models.LPMediaModel;
import com.baijiahulian.livecore.models.LPUserModel;
import com.baijiahulian.livecore.models.imodels.IMediaControlModel;
import com.baijiahulian.livecore.models.imodels.IMediaModel;
import com.baijiahulian.livecore.models.imodels.IUserModel;
import com.baijiahulian.livecore.utils.LPBackPressureBufferedSubscriber;
import com.baijiahulian.livecore.utils.LPErrorPrintSubscriber;
import com.baijiahulian.livecore.utils.LPSubscribeObjectWithLastValue;
import com.baijiahulian.livecore.wrapper.LPPlayer;
import com.baijiahulian.livecore.wrapper.LPRecorder;

import java.util.ArrayList;
import java.util.List;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;

import static com.baijiahulian.live.ui.speakerspanel.SpeakersContract.PPT_TAG;
import static com.baijiahulian.live.ui.speakerspanel.SpeakersContract.RECORD_TAG;
import static com.baijiahulian.live.ui.speakerspanel.SpeakersContract.VIEW_TYPE_APPLY;
import static com.baijiahulian.live.ui.speakerspanel.SpeakersContract.VIEW_TYPE_PPT;
import static com.baijiahulian.live.ui.speakerspanel.SpeakersContract.VIEW_TYPE_PRESENTER;
import static com.baijiahulian.live.ui.speakerspanel.SpeakersContract.VIEW_TYPE_RECORD;
import static com.baijiahulian.live.ui.speakerspanel.SpeakersContract.VIEW_TYPE_SPEAKER;
import static com.baijiahulian.live.ui.speakerspanel.SpeakersContract.VIEW_TYPE_VIDEO_PLAY;
import static com.baijiahulian.live.ui.utils.Precondition.checkNotNull;

/**
 * 发言者列表遵从{[PPT]---[主讲人视频|头像]---[自己视频]---[其他人视频]---[其他发言用户音频]---[请求发言用户]}的顺序
 * 如果全屏或没有对应的项目则不在此列表中
 * currentFullScreenTag 为当前全屏的tag VideoView为UserId
 * Created by Shubo on 2017/6/5.
 */

public class SpeakerPresenter implements SpeakersContract.Presenter {

    private LiveRoomRouterListener routerListener;
    private SpeakersContract.View view;
    private LPSubscribeObjectWithLastValue<String> fullScreenKVO;
    private int MAX_VIDEO_COUNT = 0;
    private boolean autoPlayPresenterVideo = true;

    // 显示视频或发言用户的分段列表 item为相应的tag或者userId;
    private List<String> displayList;

    // 分段列表各个段位的下标 维护需谨慎
    private int _displayPresenterSection = -1; //主讲人分段
    private int _displayRecordSection = -1;    //自己视频
    private int _displayVideoSection = -1;     //视频发言分段
    private int _displaySpeakerSection = -1;   //未开视频发言分段
    private int _displayApplySection = -1;     //请求发言用户分段

    private Subscription subscriptionOfMediaNew, subscriptionOfMediaChange, subscriptionOfMediaClose,
            subscriptionSpeakApply, subscriptionSpeakResponse, subscriptionOfActiveUser, subscriptionOfFullScreen,
            subscriptionOfUserOut;

    public SpeakerPresenter(SpeakersContract.View view) {
        this.view = view;
        fullScreenKVO = new LPSubscribeObjectWithLastValue<>(PPT_TAG);
    }

    @Override
    public void setRouter(LiveRoomRouterListener liveRoomRouterListener) {
        routerListener = liveRoomRouterListener;
        MAX_VIDEO_COUNT = routerListener.getLiveRoom().getPlayer().getMaxSupportedVideoSize() - 1;
    }

    private void initView() {
        displayList = new ArrayList<>();
        if (!fullScreenKVO.getParameter().equals(PPT_TAG)) {
            displayList.add(PPT_TAG);
        }
        _displayPresenterSection = displayList.size();
        if (routerListener.getLiveRoom().getPresenterUser() != null) {
            displayList.add(routerListener.getLiveRoom().getPresenterUser().getUserId());
        }
        _displayRecordSection = displayList.size();
        if (routerListener.getLiveRoom().getRecorder().isVideoAttached()) {
            displayList.add(RECORD_TAG);
        }
        _displayVideoSection = displayList.size();
        _displaySpeakerSection = displayList.size();
        for (IMediaModel model : routerListener.getLiveRoom().getSpeakQueueVM().getSpeakQueueList()) {
            if (!model.getUser().getUserId().equals(routerListener.getLiveRoom().getPresenterUser().getUserId())) {
                // exclude presenter
                displayList.add(model.getUser().getUserId());
            }
        }
        _displayApplySection = displayList.size();
        for (IUserModel model : routerListener.getLiveRoom().getSpeakQueueVM().getApplyList()) {
            displayList.add(model.getUserId());
        }
        // init view
        for (int i = 0; i < displayList.size(); i++) {
            view.notifyItemInserted(i);
        }
    }

    @Override
    public void subscribe() {

        LPErrorPrintSubscriber<List<IMediaModel>> activeUserSubscriber = new LPErrorPrintSubscriber<List<IMediaModel>>() {
            @Override
            public void call(List<IMediaModel> iMediaModels) {
                initView();
            }
        };
        final ConnectableObservable<List<IMediaModel>> observable = routerListener.getLiveRoom().getSpeakQueueVM().getObservableOfActiveUsers();
        subscriptionOfActiveUser = observable.observeOn(AndroidSchedulers.mainThread()).subscribe(activeUserSubscriber);
        observable.connect();
        routerListener.getLiveRoom().getSpeakQueueVM().requestActiveUsers();

        subscriptionOfMediaNew = routerListener.getLiveRoom().getSpeakQueueVM().getObservableOfMediaNew()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new LPErrorPrintSubscriber<IMediaModel>() {
                    @Override
                    public void call(IMediaModel iMediaModel) {
                        if (routerListener.getLiveRoom().getPresenterUser() != null
                                && iMediaModel.getUser().getUserId().equals(routerListener.getLiveRoom().getPresenterUser().getUserId())) {
                            if (_displayPresenterSection < displayList.size() &&
                                    displayList.get(_displayPresenterSection).equals(iMediaModel.getUser().getUserId())) {
                                view.notifyItemChanged(_displayPresenterSection);
                            } else {
                                displayList.add(_displayPresenterSection, iMediaModel.getUser().getUserId());
                                _displayRecordSection++;
                                _displayVideoSection++;
                                _displaySpeakerSection++;
                                _displayApplySection++;
                                view.notifyItemInserted(_displayPresenterSection);
                            }
                            printSections();
                            return;
                        }
                        displayList.add(_displayApplySection, iMediaModel.getUser().getUserId());
                        _displayApplySection++;
                        view.notifyItemInserted(_displayApplySection - 1);
                        printSections();
                    }
                });

        subscriptionOfMediaChange = routerListener.getLiveRoom().getSpeakQueueVM().getObservableOfMediaChange()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new LPErrorPrintSubscriber<IMediaModel>() {
                    @Override
                    public void call(IMediaModel iMediaModel) {
                        if (fullScreenKVO.getParameter().equals(iMediaModel.getUser().getUserId())) {
                            // full screen user
                            if (!iMediaModel.isVideoOn()) {
                                fullScreenKVO.setParameter(null);
                                if (routerListener.getLiveRoom().getPresenterUser() != null
                                        && iMediaModel.getUser().getUserId().equals(routerListener.getLiveRoom().getPresenterUser().getUserId())) {
                                    _displayRecordSection++;
                                    _displayVideoSection++;
                                    _displaySpeakerSection++;
                                    _displayApplySection++;
                                    displayList.add(_displayPresenterSection, iMediaModel.getUser().getUserId());
                                    view.notifyItemInserted(_displayPresenterSection);
                                } else {
                                    routerListener.getLiveRoom().getPlayer().playAVClose(iMediaModel.getUser().getUserId());
                                    routerListener.getLiveRoom().getPlayer().playAudio(iMediaModel.getUser().getUserId());
                                    _displayApplySection++;
                                    displayList.add(_displayApplySection - 1, iMediaModel.getUser().getUserId());
                                    view.notifyItemInserted(_displayApplySection - 1);
                                }
                            }
                            printSections();
                            return;
                        }
                        int position = displayList.indexOf(iMediaModel.getUser().getUserId());
                        if (position == -1) { // 未在speaker列表
                            return;
                        }
                        if (position == _displayPresenterSection) {
                            view.notifyItemChanged(position);
                            printSections();
                            return;
                        }
                        if (position < _displayVideoSection) {
                            throw new RuntimeException("position < _displayVideoSection");
                        } else if (position < _displaySpeakerSection) { // 视频打开用户
                            if (!iMediaModel.isVideoOn()) { // 通知视频关闭
                                view.notifyItemDeleted(position);
                                routerListener.getLiveRoom().getPlayer().playAVClose(getItem(position));
                                routerListener.getLiveRoom().getPlayer().playAudio(getItem(position));
                                String item = displayList.remove(position);
                                displayList.add(_displayApplySection - 1, item);
                                _displaySpeakerSection--;
                                view.notifyItemInserted(_displayApplySection - 1);
                            }
                        } else if (position < _displayApplySection) { // 视频未打开用户
                            view.notifyItemChanged(position);
                        } else {
                            throw new RuntimeException("position > _displayApplySection");
                        }
                        printSections();
                    }
                });

        subscriptionOfMediaClose = routerListener.getLiveRoom().getSpeakQueueVM().getObservableOfMediaClose()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new LPErrorPrintSubscriber<IMediaModel>() {
                    @Override
                    public void call(IMediaModel iMediaModel) {
                        if (fullScreenKVO.getParameter().equals(iMediaModel.getUser().getUserId())) {
                            // full screen user
                            fullScreenKVO.setParameter(null);
                            if (routerListener.getLiveRoom().getPresenterUser() != null
                                    && iMediaModel.getUser().getUserId().equals(routerListener.getLiveRoom().getPresenterUser().getUserId())) {
                                _displayRecordSection++;
                                _displayVideoSection++;
                                _displaySpeakerSection++;
                                _displayApplySection++;
                                displayList.add(_displayPresenterSection, iMediaModel.getUser().getUserId());
                                view.notifyItemInserted(_displayPresenterSection);
                            }
                            printSections();
                            return;
                        }
                        int position = displayList.indexOf(iMediaModel.getUser().getUserId());
                        if (position == -1)
                            return;
                        if (position < _displayPresenterSection) {
                            throw new RuntimeException("position < _displayPresenterSection");
                        } else if (position < _displayVideoSection) {
                            if (routerListener.getLiveRoom().getPresenterUser() == null) {
                                view.notifyItemDeleted(position);
                                displayList.remove(position);
                                _displayVideoSection--;
                                _displayRecordSection--;
                                _displaySpeakerSection--;
                                _displayApplySection--;
                            } else {
                                view.notifyItemChanged(position);
                            }
                        } else if (position < _displaySpeakerSection) { // 视频打开用户
                            view.notifyItemDeleted(position);
                            displayList.remove(position);
                            _displaySpeakerSection--;
                            _displayApplySection--;
                        } else if (position < _displayApplySection) { // 视频未打开用户
                            view.notifyItemDeleted(position);
                            displayList.remove(position);
                            _displayApplySection--;
                        } else {
//                            throw new RuntimeException("position > _displayApplySection");
                        }
                        printSections();
                    }
                });

        subscriptionOfUserOut = routerListener.getLiveRoom().getObservableOfUserOut().observeOn(AndroidSchedulers.mainThread())
                .filter(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String s) {
                        return routerListener.getLiveRoom().getPresenterUser() == null ||
                                routerListener.getLiveRoom().getPresenterUser() .getUserId().equals(s); // 主讲人退出教室
                    }
                })
                .subscribe(new LPErrorPrintSubscriber<String>() {
                    @Override
                    public void call(String s) {
                        if (s.equals(fullScreenKVO.getParameter())) {
//                            fullScreenKVO.setParameter(null);
                            printSections();
                            return;
                        }
                        if (displayList.indexOf(s) < 0) return;
                        view.notifyItemDeleted(_displayPresenterSection);
                        displayList.remove(_displayPresenterSection);
                        _displayRecordSection--;
                        _displayVideoSection--;
                        _displaySpeakerSection--;
                        _displayApplySection--;
                        printSections();
                    }
                });

        if (routerListener.isTeacherOrAssistant()) {

            subscriptionSpeakApply = routerListener.getLiveRoom().getSpeakQueueVM().getObservableOfSpeakApply()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new LPErrorPrintSubscriber<IMediaModel>() {
                        @Override
                        public void call(IMediaModel iMediaModel) {
                            displayList.add(iMediaModel.getUser().getUserId());
                            view.notifyItemInserted(displayList.size() - 1);
                        }
                    });

            subscriptionSpeakResponse = routerListener.getLiveRoom().getSpeakQueueVM().getObservableOfSpeakResponse()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new LPErrorPrintSubscriber<IMediaControlModel>() {
                        @Override
                        public void call(IMediaControlModel iMediaControlModel) {
                            int position = displayList.indexOf(iMediaControlModel.getUser().getUserId());
                            if (position < _displayApplySection) {
                                throw new RuntimeException("position < _displayApplySection");
                            } else if (position < displayList.size()) {
                                view.notifyItemDeleted(position);
                                displayList.remove(position);
                            } else {
                                throw new RuntimeException("position > displayList.size()");
                            }
                        }
                    });
        }

        subscriptionOfFullScreen = fullScreenKVO.newObservableOfParameterChanged()
                .onBackpressureBuffer()
                .filter(new Func1<String, Boolean>() {
                    @Override
                    public Boolean call(String s) {
                        return s == null || !s.equals(fullScreenKVO.getLastParameter());
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new LPBackPressureBufferedSubscriber<String>() {
                    @Override
                    public void call(String s) {
                        if (TextUtils.isEmpty(s)) {
                            // full screen ppt
                            fullScreenKVO.setParameter(PPT_TAG);
                        } else {
                            String lastTag = fullScreenKVO.getLastParameter();
                            String tag = fullScreenKVO.getParameter();

                            View view1 = routerListener.removeFullScreenView();
                            View view2 = view.removeViewAt(displayList.indexOf(tag));

                            displayList.remove(tag);
                            if (PPT_TAG.equals(tag)) {
                                _displayPresenterSection--;
                                _displayRecordSection--;
                                _displayVideoSection--;
                                _displaySpeakerSection--;
                                _displayApplySection--;
                            } else if (routerListener.getLiveRoom().getPresenterUser() != null
                                    && tag.equals(routerListener.getLiveRoom().getPresenterUser().getUserId())) {
                                _displayRecordSection--;
                                _displayVideoSection--;
                                _displaySpeakerSection--;
                                _displayApplySection--;
                            } else if (RECORD_TAG.equals(tag)) {
                                _displayVideoSection--;
                                _displaySpeakerSection--;
                                _displayApplySection--;
                            } else { // video
                                _displaySpeakerSection--;
                                _displayApplySection--;
                            }

                            int position = -1;
                            if (!TextUtils.isEmpty(lastTag)) {
                                if (PPT_TAG.equals(lastTag)) {
                                    position = 0;
                                    displayList.add(0, PPT_TAG);
                                    _displayPresenterSection++;
                                    _displayRecordSection++;
                                    _displayVideoSection++;
                                    _displaySpeakerSection++;
                                    _displayApplySection++;
                                } else if (routerListener.getLiveRoom().getPresenterUser() != null
                                        && lastTag.equals(routerListener.getLiveRoom().getPresenterUser().getUserId())) {
                                    position = _displayPresenterSection;
                                    displayList.add(position, lastTag);
                                    _displayRecordSection++;
                                    _displayVideoSection++;
                                    _displaySpeakerSection++;
                                    _displayApplySection++;
                                } else if (RECORD_TAG.equals(lastTag)) {
                                    position = _displayRecordSection;
                                    displayList.add(position, RECORD_TAG);
                                    _displayVideoSection++;
                                    _displaySpeakerSection++;
                                    _displayApplySection++;
                                } else { // video
                                    position = _displaySpeakerSection;
                                    displayList.add(position, lastTag);
                                    _displaySpeakerSection++;
                                    _displayApplySection++;
                                }
                            }

                            if (!TextUtils.isEmpty(lastTag))
                                view.notifyViewAdded(view1, position);
                            routerListener.setFullScreenView(view2);
                        }
                        printSections();
                    }
                });
    }

    @Override
    public void unSubscribe() {
        RxUtils.unSubscribe(subscriptionOfMediaNew);
        RxUtils.unSubscribe(subscriptionOfMediaChange);
        RxUtils.unSubscribe(subscriptionOfMediaClose);
        RxUtils.unSubscribe(subscriptionSpeakApply);
        RxUtils.unSubscribe(subscriptionSpeakResponse);
        RxUtils.unSubscribe(subscriptionOfActiveUser);
        RxUtils.unSubscribe(subscriptionOfFullScreen);
        RxUtils.unSubscribe(subscriptionOfUserOut);
    }

    @Override
    public void destroy() {
        view = null;
        routerListener = null;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < 0)
            throw new RuntimeException("position < 0 in getItemViewType");
        else if (position < _displayPresenterSection)
            return VIEW_TYPE_PPT;
        else if (position < _displayRecordSection)
            return VIEW_TYPE_PRESENTER;
        else if (position < _displayVideoSection)
            return VIEW_TYPE_RECORD;
        else if (position < _displaySpeakerSection)
            return VIEW_TYPE_VIDEO_PLAY;
        else if (position < _displayApplySection)
            return VIEW_TYPE_SPEAKER;
        else if (position < displayList.size())
            return VIEW_TYPE_APPLY;
        else
            throw new RuntimeException("position > displayList.size() in getItemViewType");
    }

    @Override
    public int getItemViewType(String userId) {
        return getItemViewType(displayList.indexOf(userId));
    }

    @Override
    public IMediaModel getSpeakModel(String userId) {
        for (IMediaModel model : routerListener.getLiveRoom().getSpeakQueueVM().getSpeakQueueList()) {
            if (model.getUser().getUserId().equals(userId)) {
                return model;
            }
        }
        return null;
    }

    @Override
    public LPRecorder getRecorder() {
        return routerListener.getLiveRoom().getRecorder();
    }

    @Override
    public LPPlayer getPlayer() {
        return routerListener.getLiveRoom().getPlayer();
    }

    @Override
    public IUserModel getApplyModel(int position) {
        String userId = displayList.get(position);
        for (IUserModel model : routerListener.getLiveRoom().getSpeakQueueVM().getApplyList()) {
            if (model.getUserId().equals(userId)) {
                return model;
            }
        }
        return null;
    }

    @Override
    public IMediaModel getSpeakModel(int position) {
        String userId = displayList.get(position);
        for (IMediaModel model : routerListener.getLiveRoom().getSpeakQueueVM().getSpeakQueueList()) {
            if (model.getUser().getUserId().equals(userId)) {
                return model;
            }
        }
        // presenter mismatching
        if (routerListener.getLiveRoom().getPresenterUser() != null &&
                userId.equals(routerListener.getLiveRoom().getPresenterUser().getUserId())) {
            LPMediaModel model = new LPMediaModel();
            model.user = (LPUserModel) routerListener.getLiveRoom().getPresenterUser();
            return model;
        }
        return null;
    }

    @Override
    public void playVideo(String userId) {
        int position = displayList.indexOf(userId);

        if (position == -1) return;
        if (getSpeakModel(position) == null || !getSpeakModel(position).isVideoOn()) return;

        if (position < _displayRecordSection) {
            autoPlayPresenterVideo = true;
            view.notifyItemChanged(position);
            return;
        }

        IMediaModel model = getSpeakModel(fullScreenKVO.getParameter());
        boolean isFullScreenStudentVideo = model != null && model.getUser().getType() == LPConstants.LPUserType.Student;

        if (_displaySpeakerSection - _displayVideoSection + (isFullScreenStudentVideo ? 1 : 0) >= MAX_VIDEO_COUNT) {
            view.showMaxVideoExceed();
            return;
        }

        view.notifyItemDeleted(position);
        displayList.remove(position);

        displayList.add(_displaySpeakerSection, userId);
        _displaySpeakerSection++;
        view.notifyItemInserted(_displaySpeakerSection - 1);
    }

    @Override
    public void closeVideo(String tag) {
        if (tag.equals(RECORD_TAG)) {
            if (routerListener.getLiveRoom().getRecorder().isVideoAttached()) {
                routerListener.detachLocalVideo();
                if (!routerListener.getLiveRoom().getRecorder().isAudioAttached()) {
                    routerListener.getLiveRoom().getRecorder().stopPublishing();
                }
            }
            return;
        } else if (tag.equals(PPT_TAG)) {
            throw new RuntimeException("close PPT? wtf");
        }
        int position = displayList.indexOf(tag);

        // 在dialog操作过程中 数据发生了变化
        if (position == -1) return;
        IMediaModel model = getSpeakModel(position);
        if (model == null) return;

        if (position < _displayRecordSection) { // presenter
            autoPlayPresenterVideo = false;
            routerListener.getLiveRoom().getPlayer().playAVClose(tag);
            if (model.isAudioOn())
                routerListener.getLiveRoom().getPlayer().playAudio(tag);
            view.notifyItemChanged(position);
            return;
        }

        routerListener.getLiveRoom().getPlayer().playAVClose(tag);
        routerListener.getLiveRoom().getPlayer().playAudio(tag);
        view.notifyItemDeleted(position);
        displayList.remove(position);
        displayList.add(_displayApplySection - 1, tag);
        _displaySpeakerSection--;
        view.notifyItemInserted(_displayApplySection - 1);
    }

    @Override
    public void closeSpeaking(String userId) {
        int position = displayList.indexOf(userId);
        if (position == -1) return;
//        if (speakList.get(position - applyList.size()).getUser().getUserId().equals(routerListener.getCurrentVideoPlayingUserId())) {
//            routerListener.playVideoClose(speakList.get(position - applyList.size()).getUser().getUserId());
//            if (!routerListener.isCurrentUserTeacher())
//                routerListener.setVideoManipulated(true);
//        }
        routerListener.getLiveRoom().getSpeakQueueVM().closeOtherSpeak(userId);
    }

    @Override
    public boolean isTeacherOrAssistant() {
        return routerListener.isTeacherOrAssistant();
    }

    @Override
    public void changeBackgroundContainerSize(boolean isShrink) {
        routerListener.changeBackgroundContainerSize(isShrink);
    }

    @Override
    public void setFullScreenTag(String tag) {
        fullScreenKVO.setParameter(tag);
    }

    @Override
    public MyPPTFragment getPPTFragment() {
        return routerListener.getPPTFragment();
    }

    @Override
    public boolean isFullScreen(String tag) {
        checkNotNull(tag);
        return tag.equals(fullScreenKVO.getParameter());
    }

    @Override
    public void switchCamera() {
        routerListener.getLiveRoom().getRecorder().switchCamera();
    }

    @Override
    public void switchPrettyFilter() {
        if (getRecorder().isBeautyFilterOn()) {
            getRecorder().closeBeautyFilter();
        } else {
            getRecorder().openBeautyFilter();
        }
    }

    private boolean isScreenCleared = false;

    @Override
    public void clearScreen() {
        isScreenCleared = !isScreenCleared;
        if (isScreenCleared) routerListener.clearScreen();
        else routerListener.unClearScreen();
    }

    @Override
    public boolean isAutoPlay() {
        return autoPlayPresenterVideo;
    }

    @Override
    public String getItem(int position) {
        if (position < displayList.size())
            return displayList.get(position);
        else
            throw new RuntimeException("position > displayList.size() in getItem");
    }

    @Override
    public int getCount() {
        return displayList.size();
    }

    @Override
    public void agreeSpeakApply(String userId) {
        int position = displayList.indexOf(userId);
        if (position == -1) {
            throw new RuntimeException("invalid userId:" + userId + " in agreeSpeakApply");
        } else {
            routerListener.getLiveRoom().getSpeakQueueVM().agreeSpeakApply(displayList.get(position));
        }
    }

    @Override
    public void disagreeSpeakApply(String userId) {
        int position = displayList.indexOf(userId);
        if (position == -1) {
            throw new RuntimeException("invalid userId:" + userId + " in disagreeSpeakApply");
        } else {
            routerListener.getLiveRoom().getSpeakQueueVM().disagreeSpeakApply(displayList.get(position));
        }
    }

    public void attachVideo() {
        if (routerListener.checkCameraPermission()) {
            if (_displayRecordSection == _displayVideoSection) {
                displayList.add(_displayRecordSection, RECORD_TAG);
                _displayVideoSection++;
                _displaySpeakerSection++;
                _displayApplySection++;
                view.notifyItemInserted(_displayRecordSection);
            }
        }
        printSections();
    }

    public void detachVideo() {
        if (RECORD_TAG.equals(fullScreenKVO.getParameter())) {
            if (getRecorder().isVideoAttached())
                getRecorder().detachVideo();
            fullScreenKVO.setParameter(null);
            return;
        }
        if (_displayRecordSection == _displayVideoSection - 1) {
            view.notifyItemDeleted(_displayRecordSection);
            displayList.remove(_displayRecordSection);
            _displayVideoSection--;
            _displaySpeakerSection--;
            _displayApplySection--;
        }
        printSections();
    }

    private void printSections() {
//        Log.e("section", _displayPresenterSection + " " + _displayRecordSection + " " + _displayVideoSection + " " +
//                _displaySpeakerSection + " " + _displayApplySection);
    }

}
