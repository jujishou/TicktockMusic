package com.freedom.lauzy.ticktockmusic.ui.activity;


import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PagerSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.freedom.lauzy.ticktockmusic.R;
import com.freedom.lauzy.ticktockmusic.base.BaseActivity;
import com.freedom.lauzy.ticktockmusic.contract.PlayContract;
import com.freedom.lauzy.ticktockmusic.event.ChangeFavoriteItemEvent;
import com.freedom.lauzy.ticktockmusic.event.PlayModeEvent;
import com.freedom.lauzy.ticktockmusic.function.RxBus;
import com.freedom.lauzy.ticktockmusic.model.SongEntity;
import com.freedom.lauzy.ticktockmusic.presenter.PlayPresenter;
import com.freedom.lauzy.ticktockmusic.service.MusicManager;
import com.freedom.lauzy.ticktockmusic.service.MusicService;
import com.freedom.lauzy.ticktockmusic.ui.adapter.PlayAdapter;
import com.freedom.lauzy.ticktockmusic.ui.fragment.PlayQueueBottomSheetFragment;
import com.freedom.lauzy.ticktockmusic.utils.HorizontalPagerScrollListener;
import com.freedom.lauzy.ticktockmusic.utils.SharePrefHelper;
import com.lauzy.freedom.data.local.LocalUtil;
import com.lauzy.freedom.librarys.common.LogUtil;
import com.lauzy.freedom.librarys.widght.TickToolbar;
import com.lauzy.freedom.librarys.widght.music.PlayPauseView;

import java.util.List;

import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.disposables.Disposable;

/**
 * Desc : 播放界面
 * Author : Lauzy
 * Date : 2017/9/14
 * Blog : http://www.jianshu.com/u/e76853f863a9
 * Email : freedompaladin@gmail.com
 */
public class PlayActivity extends BaseActivity<PlayPresenter> implements
        SeekBar.OnSeekBarChangeListener, PlayPauseView.PlayPauseListener, PlayContract.View,
        MusicManager.PlayProgressListener {

    @BindView(R.id.toolbar_common)
    TickToolbar mToolbarCommon;
    @BindView(R.id.txt_current_progress)
    TextView mTxtCurrentProgress;
    @BindView(R.id.seek_play)
    SeekBar mSeekPlay;
    @BindView(R.id.txt_total_length)
    TextView mTxtTotalLength;
    @BindView(R.id.img_play_mode)
    ImageView mImgPlayMode;
    @BindView(R.id.play_pause)
    PlayPauseView mPlayPause;
    @BindView(R.id.layout_play)
    LinearLayout mLayoutPlay;
    @BindView(R.id.img_favorite)
    ImageView mImgFavorite;
    @BindView(R.id.img_play_bg)
    ImageView mImageViewBg;
    @BindView(R.id.rv_play_view)
    RecyclerView mRvPlayView;
    @SuppressWarnings("unused")
    private static final String TAG = "PlayActivity";
    private boolean mIsFavorite;
    private PlayAdapter mAdapter;
    private int mCurrentPos;

    public static Intent newInstance(Context context) {
        return new Intent(context, PlayActivity.class);
    }

    @Override
    public Context getContext() {
        return PlayActivity.this;
    }

    @Override
    protected void initInject() {
        getActivityComponent().inject(this);
    }

    @Override
    protected int getLayoutRes() {
        return R.layout.activity_play;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //订阅播放模式设置View
        Disposable playModeDisposable = RxBus.INSTANCE.doDefaultSubscribe(PlayModeEvent.class,
                playModeEvent -> setModeView());
        RxBus.INSTANCE.addDisposable(this, playModeDisposable);
    }

    @Override
    protected void initViews() {
        showBackIcon();
        setModeView();
        mToolbarCommon.setBackgroundColor(Color.TRANSPARENT);
    }

    /**
     * 设置播放模式
     */
    private void setModeView() {
        switch (SharePrefHelper.getRepeatMode(this)) {
            case MusicService.REPEAT_SINGLE_MODE:
                mImgPlayMode.setImageResource(R.drawable.ic_repeat_one_black);
                break;
            case MusicService.REPEAT_ALL_MODE:
                mImgPlayMode.setImageResource(R.drawable.ic_repeat_black);
                break;
            case MusicService.REPEAT_RANDOM_MODE:
                mImgPlayMode.setImageResource(R.drawable.ic_shuffle_black);
                break;
        }
    }

    @Override
    protected void loadData() {

        setCurProgress((int) MusicManager.getInstance().getCurrentProgress(),
                MusicManager.getInstance().getDuration());
        mPresenter.isFavoriteSong((int) MusicManager.getInstance().getCurrentSong().id);
        mTxtCurrentProgress.setText(LocalUtil.formatTime(MusicManager.getInstance().getCurrentProgress()));

        if (MusicManager.getInstance().getMusicService().getSongData() != null) {
            setUpRecyclerView();
        }

        MusicManager.getInstance().setPlayProgressListener(this);
        mSeekPlay.setOnSeekBarChangeListener(this);
        mPlayPause.setPlayPauseListener(this);
        currentPlay(MusicManager.getInstance().getCurrentSong());
    }

    private void setUpRecyclerView() {
        List<SongEntity> songData = MusicManager.getInstance().getMusicService().getSongData();
        mAdapter = new PlayAdapter(this, songData);
        mRvPlayView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mRvPlayView.setAdapter(mAdapter);
        //跳转至指定位置
        int playPosition = mAdapter.getPlayPosition(MusicManager.getInstance().getCurPosition());
        mRvPlayView.getLayoutManager().scrollToPosition(playPosition);
//        mRvPlayView.scrollToPosition(mAdapter.getPlayPosition(songData));
        new PagerSnapHelper().attachToRecyclerView(mRvPlayView);
        mCurrentPos = playPosition;
        //设置pager变化监听
        HorizontalPagerScrollListener pagerScrollListener = new HorizontalPagerScrollListener();
        pagerScrollListener.setLastPosition(playPosition);
        pagerScrollListener.setOnPageChangeListener((lastPos, currentPos) -> {
            if (lastPos > currentPos) {
                MusicManager.getInstance().skipToPrevious();
                mAdapter.updateAnimView(currentPos);
            } else if (lastPos < currentPos) {
                MusicManager.getInstance().skipToNext();
                mAdapter.updateAnimView(currentPos);
            }
            mCurrentPos = currentPos;
        });
        if (MusicManager.getInstance().isPlaying()) {
            mAdapter.startRotation();
        }
        mRvPlayView.addOnScrollListener(pagerScrollListener);
    }

    @Override
    public void currentPlay(SongEntity songEntity) {
        if (songEntity != null) {
            mToolbarCommon.setTitle(songEntity.title);
            mToolbarCommon.setSubtitle(songEntity.artistName);
            mTxtTotalLength.setText(songEntity.songLength);
            mPresenter.setCoverImgUrl(songEntity.albumCover);
            mPresenter.isFavoriteSong(songEntity.id);

            if (MusicManager.getInstance().isPlaying() && !mPlayPause.isPlaying()) {
                mPlayPause.playWithoutAnim();
            }
        }
    }

    @Override
    public void onProgress(int progress, int duration) {
        setCurProgress(progress, duration);
    }

    @Override
    public void onPlayerPause() {
        if (mPlayPause.isPlaying()) {
            mPlayPause.pause();
        }
    }

    @Override
    public void onPlayerResume() {
        if (!mPlayPause.isPlaying()) {
            mPlayPause.play();
        }
    }

    @Override
    public void updateQueue(int position) {
//        mPagerAdapter.notifyDataSetChanged();
//        mVpPlayView.setCurrentItem(MusicManager.getInstance().getCurPosition() + 1, false);
//        startRotate(false, 0);
        List<SongEntity> songData = MusicManager.getInstance().getSongData();
        if (songData == null || songData.isEmpty()) {
            return;
        }
        LogUtil.e("TAG","pos is -- " + position + " curPos is -- " + MusicManager.getInstance().getCurPosition());
        mAdapter.setData(songData);
        int playPosition = mAdapter.getPlayPosition(MusicManager.getInstance().getCurPosition());
        mRvPlayView.getLayoutManager().scrollToPosition(playPosition);
        mAdapter.updateAnimView(playPosition);
    }

    private void setCurProgress(int progress, int duration) {
        mSeekPlay.setMax(duration);
        mSeekPlay.setProgress(progress);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        mTxtCurrentProgress.setText(LocalUtil.formatTime(seekBar.getProgress()));
        if (fromUser) {
            MusicManager.getInstance().pauseProgress();
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        MusicManager.getInstance().resumeProgress();
        MusicManager.getInstance().seekTo(seekBar.getProgress());
        if (!mPlayPause.isPlaying()) {
            mPlayPause.play();
            mAdapter.startRotation();
        }
    }

    @Override
    public void play() {
        if (MusicManager.getInstance().getCurrentSong() != null) {
            MusicManager.getInstance().start();
            mAdapter.startRotation();
        }
    }

    @Override
    public void pause() {
        if (MusicManager.getInstance().getCurrentSong() != null) {
            MusicManager.getInstance().pause();
            mAdapter.pauseRotation();
        }
    }


    @Override
    public void setCoverBackground(Bitmap background) {
        mImageViewBg.setImageBitmap(background);
        mImageViewBg.setColorFilter(ContextCompat.getColor(this, R.color.colorDarkerTransparent),
                PorterDuff.Mode.SRC_OVER);
    }

    @Override
    public void addFavoriteSong() {
        setImageTint();
    }

    @Override
    public void deleteFavoriteSong() {
        mImgFavorite.setImageResource(R.drawable.ic_favorite_border_white);
    }

    @Override
    public void isFavoriteSong(boolean isFavorite) {
        mIsFavorite = isFavorite;
        mImgFavorite.setImageResource(isFavorite ? R.drawable.ic_favorite_white :
                R.drawable.ic_favorite_border_white);
        if (isFavorite) {
            setImageTint();
        }
    }

    /**
     * 设置喜欢图标
     */
    private void setImageTint() {
        Drawable drawable = ContextCompat.getDrawable(this, R.drawable.ic_favorite_white);
        DrawableCompat.setTint(drawable, ContextCompat.getColor(this, R.color.red_trans));
        mImgFavorite.setImageDrawable(drawable);
    }

    @OnClick({R.id.img_play_mode, R.id.img_play_previous, R.id.img_play_next, R.id.img_play_queue,
            R.id.img_favorite})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.img_play_mode:
                switchMode();
                break;
            case R.id.img_play_previous:
                playPrevious();
                break;
            case R.id.img_play_next:
                playNext();
                break;
            case R.id.img_play_queue:
                PlayQueueBottomSheetFragment sheetFragment = new PlayQueueBottomSheetFragment();
                sheetFragment.show(getSupportFragmentManager(), sheetFragment.getTag());
                break;
            case R.id.img_favorite:
                addOrDeleteFavoriteSong();
                break;
        }
    }

    private void playNext() {
//        MusicManager.getInstance().skipToNext();
        mRvPlayView.getLayoutManager().smoothScrollToPosition(mRvPlayView, null, ++mCurrentPos);
        refreshFavoriteIcon();
    }

    private void playPrevious() {
//        MusicManager.getInstance().skipToPrevious();
        mRvPlayView.getLayoutManager().smoothScrollToPosition(mRvPlayView, null, --mCurrentPos);
        refreshFavoriteIcon();
    }

    /**
     * 切换歌曲时刷新喜欢图标
     */
    private void refreshFavoriteIcon() {
        new Handler().postDelayed(() -> mPresenter.isFavoriteSong(MusicManager.getInstance()
                .getCurrentSong().id), 50);
    }

    /**
     * 添加删除喜欢歌曲
     */
    private void addOrDeleteFavoriteSong() {
        if (!mIsFavorite) {
            mPresenter.addFavoriteSong(MusicManager.getInstance().getCurrentSong());
        } else {
            mPresenter.deleteFavoriteSong(MusicManager.getInstance().getCurrentSong().id);
        }
        mIsFavorite = !mIsFavorite;
        //若Navigation目录为喜欢的歌曲，则发送事件，更新喜欢列表
        RxBus.INSTANCE.post(new ChangeFavoriteItemEvent());
    }

    private void switchMode() {
        switch (SharePrefHelper.getRepeatMode(this)) {
            case MusicService.REPEAT_SINGLE_MODE:
                SharePrefHelper.setRepeatMode(this, MusicService.REPEAT_RANDOM_MODE);
                mImgPlayMode.setImageResource(R.drawable.ic_shuffle_black);
                break;
            case MusicService.REPEAT_ALL_MODE:
                SharePrefHelper.setRepeatMode(this, MusicService.REPEAT_SINGLE_MODE);
                mImgPlayMode.setImageResource(R.drawable.ic_repeat_one_black);
                break;
            case MusicService.REPEAT_RANDOM_MODE:
                SharePrefHelper.setRepeatMode(this, MusicService.REPEAT_ALL_MODE);
                mImgPlayMode.setImageResource(R.drawable.ic_repeat_black);
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        RxBus.INSTANCE.dispose(this);
    }
}