package com.baijiahulian.live.ui.quiz;

import com.baijiahulian.live.ui.activity.LiveRoomRouterListener;
import com.baijiahulian.livecore.models.LPJsonModel;
import com.baijiahulian.livecore.utils.LPErrorPrintSubscriber;

import rx.Subscription;

/**
 * Created by wangkangfei on 17/5/31.
 */

public class QuizDialogPresenter implements QuizDialogContract.Presenter {
    private QuizDialogContract.View view;
    private LiveRoomRouterListener routerListener;

    public QuizDialogPresenter(QuizDialogContract.View view) {
        this.view = view;
    }

    @Override
    public void onStartArrived(LPJsonModel jsonModel) {
        view.onStartArrived(jsonModel);
    }

    @Override
    public void onQuizResArrived(LPJsonModel jsonModel) {

    }

    @Override
    public void setRouter(LiveRoomRouterListener liveRoomRouterListener) {
        this.routerListener = liveRoomRouterListener;
    }

    @Override
    public void subscribe() {
    }

    @Override
    public void unSubscribe() {

    }

    @Override
    public void destroy() {
        view = null;
        routerListener = null;
    }

    @Override
    public void submitAnswer(String submitContent) {
        routerListener.getLiveRoom().getQuizVM().sendSubmit(submitContent);
    }

    @Override
    public void sendCommonRequest(String request) {
        routerListener.getLiveRoom().getQuizVM().sendCommonRequest(request);
    }

    @Override
    public void getCurrentUser() {
        view.onGetCurrentUser(routerListener.getLiveRoom().getCurrentUser());
    }

    @Override
    public String getRoomToken() {
        return routerListener.getLiveRoom().getQuizVM().getRoomToken();
    }

    @Override
    public void dismissDlg() {
        routerListener.dismissQuizDlg();
    }
}
