package com.ensoft.imgurviewer.view.fragment;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ensoft.imgurviewer.App;
import com.ensoft.imgurviewer.service.TimeService;
import com.ensoft.imgurviewer.service.event.OnViewLockStateChange;
import com.ensoft.imgurviewer.service.listener.OnPreparedListener;
import com.ensoft.imgurviewer.view.helper.MetricsHelper;
import com.ensoft.imgurviewer.view.helper.ViewHelper;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;
import com.imgurviewer.R;

import org.greenrobot.eventbus.EventBus;

public class MediaPlayerFragment extends Fragment implements SeekBar.OnSeekBarChangeListener, Player.Listener
{
	private static final String TAG = MediaPlayerFragment.class.getCanonicalName();
	
	protected View seekBarContainer;
	protected View buttonsContainer;
	protected PlayerView videoView;
	protected ImageView playPauseView;
	protected ImageView audioOnOffView;
	protected ImageView fullscreenOnOffView;
	protected ImageView screenLockOnOff;
	protected TextView timeTextView;
	protected TextView timePlayedTextView;
	protected SeekBar seekBarView;
	protected OnPreparedListener userOnPreparedListener;
	protected Handler seekBarHandler = new Handler();
	protected Rect margins = new Rect( 0, 0, 0, 0 );
	protected boolean initialized = false;
	protected boolean isMuted = false;
	protected boolean isFirstPlaybackReady = true;
	protected boolean fullscreenEnabled = true;
	protected boolean screenLockEnabled = false;
	protected boolean fullscreenStateEnabled = false;
	protected boolean screenLockStateEnabled = false;
	protected View view;
	protected Player player;
	
	protected void updatePlayPauseState()
	{
		if ( null != player )
		{
			if ( player.isPlaying() )
			{
				pause();
			}
			else
			{
				play();
			}
		}
	}
	
	public void pause()
	{
		if ( player.isPlaying() )
		{
			player.pause();
			playPauseView.setImageResource( R.drawable.ic_play_circle_outline_white_48dp );
		}
	}
	
	public void play()
	{
		if ( !player.isPlaying() )
		{
			player.play();
			playPauseView.setImageResource( R.drawable.ic_pause_circle_outline_white_48dp );
		}
	}
	
	protected void setAudioOnOffState()
	{
		if ( null != player )
		{
			if ( isMuted )
			{
				mute();
				audioOnOffView.setImageResource( R.drawable.ic_volume_off_white_48dp );
			}
			else
			{
				unmute();
				audioOnOffView.setImageResource( R.drawable.ic_volume_up_white_48dp );
			}
		}
	}
	
	protected void toggleAudioOnOffState()
	{
		if ( null != player )
		{
			if ( isMuted )
			{
				unmute();
				audioOnOffView.setImageResource( R.drawable.ic_volume_up_white_48dp );
			}
			else
			{
				mute();
				audioOnOffView.setImageResource( R.drawable.ic_volume_off_white_48dp );
			}
		}
	}
	
	protected View.OnClickListener playPauseOnClickListener = v -> updatePlayPauseState();
	
	protected View.OnClickListener audioOnOffListener = v -> toggleAudioOnOffState();
	
	public static MediaPlayerFragment newInstance( boolean fullscreenEnabled, boolean screenLockEnabled )
	{
		Bundle args = new Bundle();
		args.putBoolean( "fullscreenEnabled", fullscreenEnabled );
		args.putBoolean( "screenLockEnabled", screenLockEnabled );
		MediaPlayerFragment fragment = new MediaPlayerFragment();
		fragment.setArguments( args );
		return fragment;
	}
	
	@Nullable
	@Override
	public View onCreateView( @NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState )
	{
		return inflater.inflate( R.layout.mediaplayer, container, false );
	}
	
	@Override
	public void onViewCreated( @NonNull View view, @Nullable Bundle savedInstanceState )
	{
		super.onViewCreated( view, savedInstanceState );
		
		this.view = view;
		
		if ( getArguments() != null )
		{
			fullscreenEnabled = getArguments().getBoolean( "fullscreenEnabled", true );
			screenLockEnabled = getArguments().getBoolean( "screenLockEnabled", false );
		}
		
		if ( null != getResources().getConfiguration() )
		{
			setOrientationMargins( getResources().getConfiguration().orientation );
		}
		
		seekBarContainer = view.findViewById( R.id.mediaPlayer_seekBarContainer );
		buttonsContainer = view.findViewById( R.id.mediaPlayer_buttonsContainer );
		
		playPauseView = view.findViewById( R.id.mediaPlayer_playPause );
		playPauseView.setOnClickListener( playPauseOnClickListener );
		
		audioOnOffView = view.findViewById( R.id.mediaPlayer_audioOnOff );
		audioOnOffView.setOnClickListener( audioOnOffListener );
		
		timeTextView = view.findViewById( R.id.mediaPlayer_time );
		timePlayedTextView = view.findViewById( R.id.mediaPlayer_timePlayed );
		
		fullscreenOnOffView = view.findViewById( R.id.mediaPlayer_fullscreenOnOff );
		screenLockOnOff = view.findViewById( R.id.mediaPlayer_screenLockOnOff );
		
		fullscreenOnOffView.setVisibility( fullscreenEnabled ? View.VISIBLE : View.GONE );
		screenLockOnOff.setVisibility( screenLockEnabled ? View.VISIBLE : View.GONE );
		
		fullscreenOnOffView.setOnClickListener( v -> setFullscreenState( !fullscreenStateEnabled ) );
		screenLockOnOff.setOnClickListener( v -> setScreenLockState( !screenLockStateEnabled ) );
		
		screenLockOnOff.getViewTreeObserver().addOnGlobalLayoutListener( new ViewTreeObserver.OnGlobalLayoutListener()
		{
			@Override
			public void onGlobalLayout()
			{
				screenLockOnOff.setPivotX( 0 );
				screenLockOnOff.setPivotY( screenLockOnOff.getHeight() );
				
				try
				{
					if ( screenLockOnOff.getViewTreeObserver().isAlive() )
					{
						screenLockOnOff.getViewTreeObserver().removeOnGlobalLayoutListener( this );
					}
				}
				catch ( Exception e )
				{
					Log.e( TAG, "Error on onGlobalLayout: " + e.toString() );
				}
			}
		} );
		
		seekBarView = view.findViewById( R.id.mediaPlayer_seekBar );
		
		if ( null != seekBarView )
		{
			seekBarView.setOnSeekBarChangeListener( this );
			
			PorterDuffColorFilter colorFilter = new PorterDuffColorFilter( getResources().getColor( R.color.imgur_color ), PorterDuff.Mode.SRC_IN );
			
			seekBarView.getProgressDrawable().setColorFilter( colorFilter );
			
			seekBarView.getThumb().setColorFilter( colorFilter );
		}
		
		if ( null != player )
		{
			init();
		}
	}
	
	protected void setScreenLockState( boolean enabled )
	{
		if ( null == getActivity() )
			return;
		
		if ( enabled )
		{
			screenLockOnOff.setAlpha( 0.15f );
			screenLockOnOff.setScaleX( 0.5f );
			screenLockOnOff.setScaleY( 0.5f );
			seekBarContainer.setVisibility( View.GONE );
			audioOnOffView.setVisibility( View.GONE );
			playPauseView.setVisibility( View.GONE );
			fullscreenOnOffView.setVisibility( View.GONE );
			buttonsContainer.setBackgroundColor( Color.TRANSPARENT );
		}
		else
		{
			screenLockOnOff.setAlpha( 1.f );
			screenLockOnOff.setScaleX( 1.f );
			screenLockOnOff.setScaleY( 1.f );
			seekBarContainer.setVisibility( View.VISIBLE );
			audioOnOffView.setVisibility( View.VISIBLE );
			playPauseView.setVisibility( View.VISIBLE );
			fullscreenOnOffView.setVisibility( View.VISIBLE );
			buttonsContainer.setBackgroundColor( getResources().getColor( R.color.toolbar_background ) );
		}
		
		screenLockStateEnabled = enabled;
		
		EventBus.getDefault().post( new OnViewLockStateChange( screenLockStateEnabled ) );
	}
	
	protected void setFullscreenState( boolean enabled )
	{
		if ( null == getActivity() )
			return;
		
		try
		{
			if ( enabled )
			{
				getActivity().setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE );
				
				fullscreenOnOffView.setImageResource( R.drawable.ic_fullscreen_exit_white_48dp );
			}
			else
			{
				getActivity().setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_USER );
				
				fullscreenOnOffView.setImageResource( R.drawable.ic_fullscreen_white_48dp );
			}
			
			fullscreenStateEnabled = enabled;
		}
		catch ( Exception ignored ) {}
	}
	
	public void setOnPreparedListener( OnPreparedListener onPreparedListener )
	{
		userOnPreparedListener = onPreparedListener;
	}
	
	public void onPlaybackStateChanged(@Player.State int state)
	{
		if ( state == Player.STATE_READY )
		{
			init();
			
			if ( isFirstPlaybackReady )
			{
				isMuted = App.getInstance().getPreferencesService().videosMuted();
				isFirstPlaybackReady = false;
			}
			
			updatePlayPauseState();
			
			setAudioOnOffState();
			
			userOnPreparedListener.onPrepared();
		}
	}
	
	public void setVideoView( PlayerView videoView )
	{
		this.videoView = videoView;
		this.player = videoView.getPlayer();
		if ( null != this.player )
			this.player.addListener( this );
		
		if ( null != playPauseView )
		{
			init();
		}
	}
	
	public void updateProgressBar()
	{
		seekBarHandler.postDelayed( seekBarRunnable, 100 );
	}
	
	private Runnable seekBarRunnable = new Runnable()
	{
		public void run()
		{
			try
			{
				if ( isAdded() )
				{
					long currentPosition = Math.max( 0, player.getCurrentPosition() );
					long duration = Math.max( 0, player.getDuration() );
					String timeLeftStr = new TimeService( getActivity() ).timeLeftFormatter( duration, currentPosition );
					String timePlayed = new TimeService( getActivity() ).timeFormatter( currentPosition / 1000L );
					
					if ( isVisible() && !timeTextView.getText().equals( timeLeftStr ) )
					{
						timeTextView.setText( timeLeftStr );
						timePlayedTextView.setText( timePlayed );
					}
					
					seekBarView.setProgress( (int)player.getCurrentPosition() );
				}
			}
			catch ( Exception e )
			{
				if ( null != e.getMessage() )
					Log.e( TAG, e.getMessage() );
			}
			
			seekBarHandler.postDelayed( this, 100 );
		}
	};
	
	protected void init()
	{
		if ( null != seekBarView && null != player )
		{
			seekBarView.setMax( (int)player.getDuration() );
			
			updateProgressBar();
			
			initialized = true;
		}
	}
	
	@Override
	public void onProgressChanged( SeekBar seekBar, int progress, boolean fromUser )
	{
	}
	
	@Override
	public void onStartTrackingTouch( SeekBar seekBar )
	{
		seekBarHandler.removeCallbacks( seekBarRunnable );
	}
	
	@Override
	public void onStopTrackingTouch( SeekBar seekBar )
	{
		seekBarHandler.removeCallbacks( seekBarRunnable );
		
		if ( null != player )
			player.seekTo( seekBar.getProgress() );
		
		updateProgressBar();
	}
	
	public void setVisibility( int visibility )
	{
		if ( null != view )
		{
			view.setVisibility( visibility );
		}
	}
	
	public void startAnimation( Animation animation )
	{
		if ( null != view )
		{
			view.startAnimation( animation );
		}
	}
	
	public void setMargins( int left, int top, int right, int bottom )
	{
		View v = view;
		
		if ( null != v )
		{
			ViewHelper.setMargins( v, left, top, right, bottom );
		}
		else
		{
			margins = new Rect( left, top, right, bottom );
		}
	}
	
	public void setOrientationMargins( int orientation )
	{
		if ( orientation == Configuration.ORIENTATION_LANDSCAPE )
		{
			setMargins( 0, 0, MetricsHelper.getNavigationBarWidth( getActivity() ), 0 );
		}
		else if ( orientation == Configuration.ORIENTATION_PORTRAIT )
		{
			if ( null != getActivity() )
			{
				setMargins( 0, 0, 0, MetricsHelper.getNavigationBarHeight( getActivity() ) +
					( ViewHelper.hasImmersive( getActivity() ) ? MetricsHelper.dpToPx( getActivity(), 8 ) : 0 )
				);
			}
		}
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		
		if ( initialized )
		{
			seekBarHandler.removeCallbacks( seekBarRunnable );
		}
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		if ( initialized )
		{
			updateProgressBar();
		}
	}
	
	public void mute()
	{
		setVolume( 0 );
		isMuted = true;
	}
	
	public void unmute()
	{
		setVolume( 100 );
		isMuted = false;
	}
	
	public void setVolume( int amount )
	{
		try
		{
			final int max = 100;
			final double numerator = max - amount > 0 ? Math.log( max - amount ) : 0;
			final float volume = (float) ( 1 - ( numerator / Math.log( max ) ) );
			if ( null != player )
				player.setVolume( volume );
		}
		catch ( Exception e )
		{
			Log.e( TAG, e.getMessage() );
		}
	}
}
