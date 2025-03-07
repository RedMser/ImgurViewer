package com.ensoft.imgurviewer.view.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Animatable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.webkit.URLUtil;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.ensoft.imgurviewer.App;
import com.ensoft.imgurviewer.model.MediaType;
import com.ensoft.imgurviewer.service.DownloadService;
import com.ensoft.imgurviewer.service.FrescoService;
import com.ensoft.imgurviewer.service.IntentUtils;
import com.ensoft.imgurviewer.service.PermissionService;
import com.ensoft.imgurviewer.service.PreferencesService;
import com.ensoft.imgurviewer.service.ResourceSolver;
import com.ensoft.imgurviewer.service.TransparencyUtils;
import com.ensoft.imgurviewer.service.UriUtils;
import com.ensoft.imgurviewer.service.event.OnViewLockStateChange;
import com.ensoft.imgurviewer.service.listener.AlbumPagerProvider;
import com.ensoft.imgurviewer.service.listener.ControllerImageInfoListener;
import com.ensoft.imgurviewer.service.listener.ResourceLoadListener;
import com.ensoft.imgurviewer.view.activity.AppActivity;
import com.ensoft.imgurviewer.view.activity.SettingsActivity;
import com.ensoft.imgurviewer.view.helper.MetricsHelper;
import com.ensoft.imgurviewer.view.helper.SlidrPositionHelper;
import com.ensoft.imgurviewer.view.helper.ViewHelper;
import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.drawee.generic.GenericDraweeHierarchy;
import com.facebook.drawee.generic.GenericDraweeHierarchyBuilder;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.decoder.DecodeException;
import com.facebook.imagepipeline.image.ImageInfo;
import com.github.piasy.biv.loader.ImageLoader;
import com.github.piasy.biv.view.BigImageView;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.imgurviewer.R;
import com.r0adkll.slidr.Slidr;
import com.r0adkll.slidr.model.SlidrConfig;
import com.r0adkll.slidr.model.SlidrInterface;
import com.r0adkll.slidr.model.SlidrListener;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.net.Proxy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import okhttp3.Call;
import okhttp3.OkHttpClient;

import static com.google.android.exoplayer2.Player.REPEAT_MODE_ALL;

public class ImageViewerFragment extends Fragment
{
	public static final String TAG = ImageViewerFragment.class.getCanonicalName();
	public static final String PARAM_RESOURCE_PATH = "resourcePath";
	public static final String PARAM_ADAPTER_POSITION = "adapterPosition";
	private static final int UI_ANIMATION_DELAY = 300;
	
	private AppActivity context;
	
	private View contentContainer;
	private View contentView;
	private ProgressBar progressBar;
	private View progressLine;
	private BigImageView imageView;
	private SimpleDraweeView imageViewFallback;
	private PlayerView videoView;
	private View videoTouchView;
	private boolean visible;
	private long lastClickTime;
	private LinearLayout floatingMenu;
	private Uri currentResource;
	private SlidrInterface slidrInterface;
	private boolean viewLocked = false;
	private boolean skipDettaching = false;
	private int adapterPosition = 0;
	protected MediaPlayerFragment mediaPlayerFragment;
	private AlbumPagerProvider albumPagerProvider;
	private boolean requestingPermissionFromShare = false;
	private ExoPlayer player;
	
	public static ImageViewerFragment newInstance( String resource )
	{
		return newInstance( resource, 0 );
	}
	
	public static ImageViewerFragment newInstance( String resource, int adapterPosition )
	{
		ImageViewerFragment imageViewerFragment = new ImageViewerFragment();
		Bundle args = new Bundle();
		args.putString( PARAM_RESOURCE_PATH, resource );
		args.putInt( PARAM_ADAPTER_POSITION, adapterPosition );
		imageViewerFragment.setArguments( args );
		return imageViewerFragment;
	}
	
	@Nullable
	@Override
	public View onCreateView( @NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState )
	{
		return inflater.inflate( R.layout.fragment_imageviewer, container, false );
	}
	
	@Override
	public void onViewCreated( @NonNull View view, @Nullable Bundle savedInstanceState )
	{
		super.onViewCreated( view, savedInstanceState );
		
		Bundle args = getArguments();
		
		adapterPosition = args != null ? args.getInt( PARAM_ADAPTER_POSITION, 0 ) : 0;
		
		context = (AppActivity) getActivity();
		
		if ( null != context )
			context.statusBarTint();
		
		visible = true;
		contentContainer = view.findViewById( R.id.content_container );
		
		contentView = context.findViewById( android.R.id.content );
		imageView = view.findViewById( R.id.imageView );
		imageViewFallback = view.findViewById( R.id.imageViewFallback );
		videoView = view.findViewById( R.id.videoView );
		videoTouchView = view.findViewById( R.id.videoTouchView );
		player = new ExoPlayer.Builder(context).build();
		videoView.setPlayer( player );
		videoView.setUseController( false );
		player.setRepeatMode( REPEAT_MODE_ALL );
		progressBar = view.findViewById( R.id.progressBar );
		progressLine = view.findViewById( R.id.progressLine );
		floatingMenu = view.findViewById( R.id.floating_menu );
		
		view.findViewById( R.id.settings ).setOnClickListener( v -> showSettings() );
		view.findViewById( R.id.download ).setOnClickListener( v -> downloadImage() );
		view.findViewById( R.id.share ).setOnClickListener( v -> shareImage() );
		
		if ( null != getResources().getConfiguration() )
		{
			setOrientation( getResources().getConfiguration().orientation );
		}
		
		contentView.setClickable( true );
		contentView.setOnClickListener( v -> toggle() );
		videoTouchView.setOnClickListener( v -> toggle() );
		
		String path = null != args ? args.getString( PARAM_RESOURCE_PATH ) : null;
		
		if ( null != path )
		{
			Uri uri = Uri.parse( path );
			
			if ( UriUtils.isContentUri( uri ) )
			{
				solveContentUri( uri );
			}
			else
			{
				loadResource( uri );
			}
		}
	}
	
	@Override
	public void onAttach( Context context )
	{
		super.onAttach( context );
		
		if ( context instanceof AlbumPagerProvider )
		{
			albumPagerProvider = (AlbumPagerProvider) context;
		}
	}
	
	protected void solveContentUri( Uri uri )
	{
		try
		{
			requestingPermissionFromShare = false;
			
			if ( !new PermissionService().askReadExternalStorageAccess( this ) )
			{
				loadResource( UriUtils.contentUriToFileUri( context, uri ) );
			}
			else
			{
				skipDettaching = true;
				currentResource = uri;
			}
		}
		catch ( Exception e )
		{
			if ( null != context )
				context.finish();
		}
	}
	
	@Override
	public void onDetach()
	{
		super.onDetach();
		
		if ( null != player )
			player.release();
	}
	
	public void setViewsMargins( int orientation )
	{
		if ( App.getInstance().getPreferencesService().isNavigationBarKeptVisible() )
		{
			int navigationBarWidth = MetricsHelper.getNavigationBarWidth( context );
			
			if ( orientation == Configuration.ORIENTATION_PORTRAIT )
			{
				ViewHelper.setMargins( imageView, 0, 0, 0, navigationBarWidth );
				ViewHelper.setMargins( imageViewFallback, 0, 0, 0, navigationBarWidth );
				ViewHelper.setMargins( videoView, 0, 0, 0, navigationBarWidth );
			}
			else if ( orientation == Configuration.ORIENTATION_LANDSCAPE )
			{
				ViewHelper.setMargins( imageView, navigationBarWidth, 0, navigationBarWidth, 0 );
				ViewHelper.setMargins( imageViewFallback, navigationBarWidth, 0, navigationBarWidth, 0 );
				ViewHelper.setMargins( videoView, navigationBarWidth, 0, navigationBarWidth, 0 );
			}
		}
		else
		{
			ViewHelper.setMargins( imageView, 0, 0, 0, 0 );
			ViewHelper.setMargins( imageViewFallback, 0, 0, 0, 0 );
			ViewHelper.setMargins( videoView, 0, 0, 0, 0 );
		}
	}
	
	public void loadResource( Uri uri )
	{
		new ResourceSolver( new ResourceLoadListener()
		{
			@Override
			public void loadVideo( Uri uri, MediaType mediaType, Uri referer )
			{
				ImageViewerFragment.this.loadVideo( uri, mediaType );
				
				if ( App.getInstance().getPreferencesService().getDisableWindowTransparency() )
					TransparencyUtils.convertActivityFromTranslucent( getActivity() );
			}
			
			@Override
			public void loadImage( Uri uri, Uri thumbnail )
			{
				ImageViewerFragment.this.loadImage( uri, thumbnail );
				
				if ( App.getInstance().getPreferencesService().getDisableWindowTransparency() )
					TransparencyUtils.convertActivityFromTranslucent( getActivity() );
			}
			
			@Override
			public void loadAlbum( Uri uri, Class<?> view )
			{
				Intent intent = new Intent( context, view );
				intent.putExtra( AppActivity.ALBUM_DATA, uri.toString() );
				startActivity( intent );
				
				if ( null != context )
					context.finish();
			}
			
			@Override
			public void loadFailed( Uri uri, String error )
			{
				Log.v( TAG, error );
				Toast.makeText( getActivity(), error, Toast.LENGTH_SHORT ).show();
			}
		} ).solve( uri );
	}
	
	private void initSlider()
	{
		PreferencesService preferencesService = App.getInstance().getPreferencesService();
		
		if ( preferencesService.gesturesEnabled() )
		{
			if ( null != slidrInterface && skipDettaching )
			{
				skipDettaching = false;
				
				return;
			}
			
			slidrInterface = Slidr.replace( contentContainer, new SlidrConfig.Builder().listener( new SlidrListener()
			{
				@Override
				public void onSlideStateChanged( int state ) {}
				
				@Override
				public void onSlideChange( float percent )
				{
					contentContainer.setBackgroundColor( (int) ( percent * 255.0f + 0.5f ) << 24 );
				}
				
				@Override
				public void onSlideOpened() {}
				
				@Override
				public boolean onSlideClosed() { return false; }
			} ).position( SlidrPositionHelper.fromString( preferencesService.getGesturesImageView() ) ).build() );
		}
	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		
		initSlider();
	}
	
	protected void onImageClick()
	{
		if ( !( System.currentTimeMillis() - lastClickTime <= UI_ANIMATION_DELAY ) )
		{
			new Handler().postDelayed( () ->
			{
				long diff = System.currentTimeMillis() - lastClickTime;
				
				if ( diff >= UI_ANIMATION_DELAY )
				{
					toggle();
				}
			}, UI_ANIMATION_DELAY );
		}
		
		lastClickTime = System.currentTimeMillis();
	}
	
	protected void createMediaPlayer()
	{
		PreferencesService preferencesService = App.getInstance().getPreferencesService();
		
		FragmentTransaction fragmentTransaction = getChildFragmentManager().beginTransaction();
		fragmentTransaction.replace( R.id.player, mediaPlayerFragment = MediaPlayerFragment.newInstance( preferencesService.fullscreenButton(), preferencesService.screenLockButton() ) );
		fragmentTransaction.commitAllowingStateLoss();
		
		mediaPlayerFragment.setVideoView( videoView );
	}
	
	public void loadImageFallback( Uri uri, Uri thumbnail )
	{
		GenericDraweeHierarchy hierarchy = new GenericDraweeHierarchyBuilder( imageViewFallback.getResources() )
			.setActualImageScaleType( ScalingUtils.ScaleType.FIT_CENTER )
			.build();
		
		imageView.setVisibility( View.GONE );
		
		progressBar.setVisibility( View.VISIBLE );
		
		progressLine.setVisibility( View.GONE );
		
		imageViewFallback.setHierarchy( hierarchy );
		
		imageViewFallback.setVisibility( View.VISIBLE );
		
		new FrescoService().loadImage( uri, thumbnail, imageViewFallback, new ControllerImageInfoListener()
		{
			@Override
			public void onFinalImageSet( String id, ImageInfo imageInfo, Animatable animatable )
			{
				progressBar.setVisibility( View.INVISIBLE );
			}
			
			@Override
			public void onFailure( String id, Throwable throwable )
			{
				Log.v( TAG, throwable.toString() );
				
				if ( throwable instanceof DecodeException )
				{
					Toast.makeText( context, context.getString( R.string.resourceInvalid ), Toast.LENGTH_SHORT ).show();
				}
				else
				{
					Toast.makeText( context, throwable.toString(), Toast.LENGTH_SHORT ).show();
				}
				
			}
		}, new Point( 0, 0 ) );
	}
	
	public void loadImage( Uri uri, Uri thumbnail )
	{
		currentResource = uri;
		
		Log.v( TAG, "Loading image: " + uri.toString() );
		
		videoView.setVisibility( View.GONE );
		videoTouchView.setVisibility( View.GONE );
		
		imageView.setOptimizeDisplay( false );
		imageView.setOnClickListener( v -> onImageClick() );
		
		if ( imageView.getSSIV() != null )
			imageView.getSSIV().setMaxScale( 10 );
		
		imageView.setImageLoaderCallback( new ImageLoader.Callback()
		{
			@Override
			public void onCacheHit( File image ) {}
			
			@Override
			public void onCacheMiss( File image ) {}
			
			@Override
			public void onStart()
			{
				progressLine.setVisibility( View.VISIBLE );
			}
			
			@Override
			public void onProgress( int progress )
			{
				progressLine.getLayoutParams().width = ( (int) ( ( (View) progressLine.getParent() ).getWidth() * ( progress / 100.f ) ) );
				progressLine.requestLayout();
			}
			
			@Override
			public void onFinish() {}
			
			@Override
			public void onSuccess( File image ) {}
			
			@Override
			public void onFail( Exception error )
			{
				imageView.setVisibility( View.GONE );
				
				Toast.makeText( context, error.getMessage(), Toast.LENGTH_SHORT ).show();
			}
		} );
		
		imageView.setOnImageEventListener( new SubsamplingScaleImageView.OnImageEventListener()
		{
			@Override
			public void onReady() {}
			
			@Override
			public void onImageLoaded()
			{
				progressBar.setVisibility( View.GONE );
				progressLine.setVisibility( View.GONE );
			}
			
			@Override
			public void onPreviewLoadError( Exception e ) {}
			
			@Override
			public void onImageLoadError( Exception e )
			{
				loadImageFallback( uri, thumbnail );
			}
			
			@Override
			public void onTileLoadError( Exception e )
			{
				loadImageFallback( uri, thumbnail );
			}
			
			@Override
			public void onPreviewReleased() {}
		} );
		
		imageView.showImage( uri.toString().hashCode(), null != thumbnail ? thumbnail : Uri.EMPTY, uri );
		
		delayedHide();
	}
	
	public void loadVideo( Uri uri, MediaType mediaType )
	{
		currentResource = uri;
		
		Log.v( TAG, "Loading video: " + uri.toString() );
		
		imageView.setVisibility( View.GONE );
		
		videoView.setOnClickListener( v -> toggle() );
		
		createMediaPlayer();
		
		mediaPlayerFragment.setOnPreparedListener( () ->
		{
			progressBar.setVisibility( View.INVISIBLE );
			
			videoView.setVisibility( View.VISIBLE );
			videoTouchView.setVisibility( View.VISIBLE );
			
			videoView.setBackgroundColor( Color.TRANSPARENT );
			
			if ( albumPagerProvider != null )
			{
				if ( albumPagerProvider.getCurrentPage() == adapterPosition )
				{
					mediaPlayerFragment.play();
				}
				else
				{
					mediaPlayerFragment.pause();
				}
			}
			else
			{
				mediaPlayerFragment.play();
			}
		} );
		
		player.addListener( new Player.Listener()
		{
			@Override
			public void onPlayerError( PlaybackException error )
			{
				Toast.makeText( getActivity(), R.string.couldNotReproduceVideo, Toast.LENGTH_SHORT ).show();
				
				progressBar.setVisibility( View.INVISIBLE );
				
			}
		} );
		
		Proxy proxy = App.getInstance().getProxyUtils().getProxy();
		DataSource.Factory dataSourceFactory;
		
		if ( proxy != null )
		{
			OkHttpClient client = new OkHttpClient.Builder().proxy( proxy ).build();
			dataSourceFactory = new OkHttpDataSource.Factory( (Call.Factory) client )
				.setUserAgent( UriUtils.getDefaultUserAgent() );
		}
		else
		{
			dataSourceFactory = new DefaultHttpDataSource.Factory()
				.setUserAgent( UriUtils.getDefaultUserAgent() );
		}
		
		MediaSource mediaSource = new DefaultMediaSourceFactory( dataSourceFactory )
			.createMediaSource( MediaItem.fromUri( uri ) );
		
		player.setMediaSource( mediaSource );
		player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setMaxVideoSizeSd().build());
		player.prepare();
		
		delayedHide();
	}
	
	@Override
	public void onRequestPermissionsResult( final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults )
	{
		super.onRequestPermissionsResult( requestCode, permissions, grantResults );
		
		if ( requestCode == PermissionService.REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION )
		{
			if ( grantResults[ 0 ] == 0 )
			{
				if ( requestingPermissionFromShare )
				{
					shareImageAsBitmap();
				}
				else
				{
					download();
				}
			}
			else
			{
				Toast.makeText( getActivity(), requestingPermissionFromShare ? R.string.cantShareNoPermission :  R.string.cantDownloadNoPermission, Toast.LENGTH_LONG ).show();
			}
		}
		else if ( requestCode == PermissionService.REQUEST_READ_EXTERNAL_STORAGE_PERMISSION )
		{
			if ( grantResults[ 0 ] == 0 )
			{
				loadResource( UriUtils.contentUriToFileUri( context, currentResource ) );
			}
			else
			{
				Toast.makeText( context, R.string.cantDisplayResourceNoPermission, Toast.LENGTH_LONG ).show();
				
				if ( null != context )
					context.finish();
			}
		}
	}
	
	public void download()
	{
		if ( null != currentResource )
		{
			new DownloadService( context ).download( currentResource, URLUtil.guessFileName( currentResource.toString(), null, null ) );
		}
	}
	
	public void showSettings()
	{
		skipDettaching = true;
		
		startActivity( new Intent( context, SettingsActivity.class ) );
	}
	
	public void downloadImage()
	{
		requestingPermissionFromShare = false;
		
		if ( !new PermissionService().askExternalStorageAccess( this ) )
		{
			download();
		}
		else
		{
			skipDettaching = true;
		}
	}
	
	public void shareImageAsBitmap()
	{
		IntentUtils.shareAsBitmapFromUri( context, currentResource, context.getString( R.string.shareUsing ) );
	}
	
	public void shareImageFromBitmap()
	{
		requestingPermissionFromShare = true;
		
		if ( !new PermissionService().askExternalStorageAccess( this ) )
		{
			shareImageAsBitmap();
		}
		else
		{
			skipDettaching = true;
		}
	}
	
	public void shareImage()
	{
		if ( currentResource != null )
		{
			new AlertDialog.Builder( context, R.style.AppDialogTheme )
				.setTitle( R.string.share_as )
				.setItems( R.array.share_as_type, ( dialog, which ) -> {
					if ( 0 == which )
					{
						String foundMime = UriUtils.getMimeType( currentResource.toString() );
						
						if ( null != foundMime && foundMime.contains( "image" ) )
						{
							shareImageFromBitmap();
						}
						else
						{
							IntentUtils.shareAsMedia( context, currentResource, getString( R.string.shareUsing ) );
						}
					}
					else
					{
						IntentUtils.shareAsTextMessage( context, getString( R.string.share ), currentResource.toString(), getString( R.string.shareUsing ) );
					}
					
					dialog.dismiss();
				} ).show();
		}
	}
	
	private void toggle()
	{
		if ( viewLocked )
			return;
		
		if ( visible )
		{
			hide();
		}
		else
		{
			show();
		}
	}
	
	private void hide()
	{
		visible = false;
		hideHandler.postDelayed( hidePart2Runnable, UI_ANIMATION_DELAY );
	}
	
	private int getSystemUiVisibilityHideFlags()
	{
		int flags = View.SYSTEM_UI_FLAG_LOW_PROFILE;
		flags |= View.SYSTEM_UI_FLAG_FULLSCREEN;
		
		if ( !App.getInstance().getPreferencesService().isNavigationBarKeptVisible() )
		{
			flags |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
			flags |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
			flags |= View.SYSTEM_UI_FLAG_IMMERSIVE;
			flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
		}
		
		return flags;
	}
	
	private void setSystemUiVisibility()
	{
		contentView.setSystemUiVisibility( getSystemUiVisibilityHideFlags() );
		
		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R )
		{
			Window window = getActivity().getWindow();
			WindowInsetsController controller = window.getInsetsController();
			
			if (controller != null)
			{
				controller.hide(WindowInsets.Type.statusBars() | ( App.getInstance().getPreferencesService().isNavigationBarKeptVisible() ? 0 : WindowInsets.Type.navigationBars() ) );
			}
		}
	}
	
	private void hideFast()
	{
		visible = false;
		
		setSystemUiVisibility();
		
		floatingMenu.setVisibility( View.INVISIBLE );
		
		context.statusBarUntint();
	}
	
	private final Runnable hidePart2Runnable = new Runnable()
	{
		@Override
		public void run()
		{
			setSystemUiVisibility();
			
			floatingMenu.setVisibility( View.VISIBLE );
			
			if ( null != mediaPlayerFragment )
			{
				mediaPlayerFragment.setVisibility( View.VISIBLE );
			}
			
			AlphaAnimation alphaAnimation = new AlphaAnimation( 1, 0 );
			alphaAnimation.setDuration( UI_ANIMATION_DELAY );
			alphaAnimation.setAnimationListener( new Animation.AnimationListener()
			{
				@Override
				public void onAnimationStart( Animation animation ) {}
				
				@Override
				public void onAnimationEnd( Animation animation )
				{
					floatingMenu.setVisibility( View.INVISIBLE );
					
					context.statusBarUntint();
					
					if ( null != mediaPlayerFragment )
					{
						mediaPlayerFragment.setVisibility( View.INVISIBLE );
					}
				}
				
				@Override
				public void onAnimationRepeat( Animation animation ) {}
			} );
			
			floatingMenu.startAnimation( alphaAnimation );
			
			if ( null != mediaPlayerFragment )
			{
				mediaPlayerFragment.startAnimation( alphaAnimation );
			}
		}
	};
	
	@SuppressLint( "InlinedApi" )
	private void show()
	{
		contentView.setSystemUiVisibility( View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN );
		
		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R )
		{
			Window window = getActivity().getWindow();
			WindowInsetsController controller = window.getInsetsController();
			
			if (controller != null)
			{
				controller.show(WindowInsets.Type.statusBars() | ( App.getInstance().getPreferencesService().isNavigationBarKeptVisible() ? 0 : WindowInsets.Type.navigationBars()));
			}
		}
		
		visible = true;
		hideHandler.removeCallbacks( hidePart2Runnable );
		
		floatingMenu.setVisibility( View.VISIBLE );
		AlphaAnimation alphaAnimation = new AlphaAnimation( 0, 1 );
		alphaAnimation.setDuration( UI_ANIMATION_DELAY );
		floatingMenu.startAnimation( alphaAnimation );
		
		if ( null != mediaPlayerFragment )
		{
			mediaPlayerFragment.setVisibility( View.VISIBLE );
			mediaPlayerFragment.startAnimation( alphaAnimation );
		}
		
		context.statusBarTint();
	}
	
	public View getContentContainer()
	{
		return contentContainer;
	}
	
	private final Handler hideHandler = new Handler();
	private final Runnable hideRunnable = this::hide;
	
	private void delayedHide()
	{
		hideHandler.removeCallbacks( hideRunnable );
		hideHandler.postDelayed( hideRunnable, 100 );
	}
	
	@Override
	public void onConfigurationChanged( Configuration newConfig )
	{
		super.onConfigurationChanged( newConfig );
		
		setOrientation( newConfig.orientation );
	}
	
	protected void setOrientation( int orientation )
	{
		setFloatingMenuOrientation( orientation );
		setViewsMargins( orientation );
		
		if ( null != mediaPlayerFragment )
		{
			mediaPlayerFragment.setOrientationMargins( orientation );
		}
	}
	
	protected void setFloatingMenuOrientation( int orientation )
	{
		if ( null != floatingMenu )
		{
			if ( orientation == Configuration.ORIENTATION_PORTRAIT )
			{
				floatingMenu.setPadding( 0, MetricsHelper.dpToPx( context, 8 ), 0, 0 );
				ViewHelper.setMargins( floatingMenu, 0, MetricsHelper.getStatusBarHeight( context ), 0, 0 );
			}
			else if ( orientation == Configuration.ORIENTATION_LANDSCAPE )
			{
				floatingMenu.setPadding( 0, MetricsHelper.dpToPx( context, 8 ), 0, 0 );
				ViewHelper.setMargins( floatingMenu, 0, MetricsHelper.getStatusBarHeight( context ), MetricsHelper.getNavigationBarWidth( context ), 0 );
			}
		}
	}
	
	@Override
	public void onStart()
	{
		super.onStart();
		
		EventBus.getDefault().register( this );
	}
	
	@Override
	public void onStop()
	{
		EventBus.getDefault().unregister( this );
		
		super.onStop();
	}
	
	@Subscribe( threadMode = ThreadMode.MAIN )
	public void onMessageEvent( OnViewLockStateChange event )
	{
		viewLocked = event.isLocked();
		
		if ( viewLocked )
		{
			hideFast();
			
			if ( null != slidrInterface )
				slidrInterface.lock();
		}
		else
		{
			show();
			
			if ( null != slidrInterface )
				slidrInterface.unlock();
		}
	}
	
	public void onViewShow()
	{
		if ( null != mediaPlayerFragment )
			mediaPlayerFragment.play();
	}
	
	public void onViewHide()
	{
		if ( null != mediaPlayerFragment )
			mediaPlayerFragment.pause();
	}
}
