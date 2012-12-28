package com.antsapps.directnavshortcut;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal;
import android.provider.ContactsContract.Data;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextUtils.TruncateAt;

/**
 * Constructs shortcut intents.
 */
public class ShortcutIntentBuilder {

  private static final String SCHEME_NAV = "google.navigation";

  private static final String[] ADDRESS_COLUMNS = {
      StructuredPostal.DISPLAY_NAME, StructuredPostal.PHOTO_ID,
      StructuredPostal.FORMATTED_ADDRESS, StructuredPostal.TYPE,
      StructuredPostal.LABEL };

  private static final int ADDRESS_DISPLAY_NAME_COLUMN_INDEX = 0;
  private static final int ADDRESS_PHOTO_ID_COLUMN_INDEX = 1;
  private static final int ADDRESS_NUMBER_COLUMN_INDEX = 2;
  private static final int ADDRESS_TYPE_COLUMN_INDEX = 3;
  private static final int ADDRESS_LABEL_COLUMN_INDEX = 4;

  private static final String[] PHOTO_COLUMNS = { Photo.PHOTO, };

  private static final int PHOTO_PHOTO_COLUMN_INDEX = 0;

  private static final String PHOTO_SELECTION = Photo._ID + "=?";

  private final OnShortcutIntentCreatedListener mListener;
  private final Context mContext;
  private final int mIconSize;
  private final int mBorderWidth;
  private final int mBorderColor;

  /**
   * Listener interface.
   */
  public interface OnShortcutIntentCreatedListener {

    /**
     * Callback for shortcut intent creation.
     *
     * @param uri
     *          the original URI for which the shortcut intent has been created.
     * @param shortcutIntent
     *          resulting shortcut intent.
     */
    void onShortcutIntentCreated(Uri uri, Intent shortcutIntent);
  }

  public ShortcutIntentBuilder(Context context,
      OnShortcutIntentCreatedListener listener) {
    mContext = context;
    mListener = listener;

    final Resources r = context.getResources();
    mIconSize = r.getDimensionPixelSize(R.dimen.shortcut_icon_size);
    mBorderWidth = r
        .getDimensionPixelOffset(R.dimen.shortcut_icon_border_width);
    mBorderColor = r.getColor(R.color.shortcut_overlay_text_background);
  }

  public void
      createNavigationShortcutIntent(Uri dataUri) {
    new AddressLoadingAsyncTask(dataUri).execute();
  }

  private final class AddressLoadingAsyncTask extends AsyncTask<Void, Void, Void> {
    protected Uri mUri;
    protected String mDisplayName;
    protected byte[] mBitmapData;
    protected long mPhotoId;
    private String mAddress;
    private int mAddressType;
    private String mAddressLabel;

    public AddressLoadingAsyncTask(Uri uri) {
      mUri = uri;
    }

    @Override
    protected Void doInBackground(Void... params) {
      loadData();
      loadPhoto();
      return null;
    }

    private void loadData() {
      ContentResolver resolver = mContext.getContentResolver();
      Cursor cursor = resolver.query(mUri, ADDRESS_COLUMNS, null, null, null);
      if (cursor != null) {
        try {
          if (cursor.moveToFirst()) {
            mDisplayName = cursor.getString(ADDRESS_DISPLAY_NAME_COLUMN_INDEX);
            mPhotoId = cursor.getLong(ADDRESS_PHOTO_ID_COLUMN_INDEX);
            mAddress = cursor.getString(ADDRESS_NUMBER_COLUMN_INDEX);
            mAddressType = cursor.getInt(ADDRESS_TYPE_COLUMN_INDEX);
            mAddressLabel = cursor.getString(ADDRESS_LABEL_COLUMN_INDEX);
          }
        } finally {
          cursor.close();
        }
      }
    }

    private void loadPhoto() {
      if (mPhotoId == 0) {
        return;
      }

      ContentResolver resolver = mContext.getContentResolver();
      Cursor cursor = resolver.query(
          Data.CONTENT_URI,
          PHOTO_COLUMNS,
          PHOTO_SELECTION,
          new String[] { String.valueOf(mPhotoId) },
          null);
      if (cursor != null) {
        try {
          if (cursor.moveToFirst()) {
            mBitmapData = cursor.getBlob(PHOTO_PHOTO_COLUMN_INDEX);
          }
        } finally {
          cursor.close();
        }
      }
    }

    @Override
    protected void onPostExecute(Void result) {
      createNavigationShortcutIntent(
          mUri,
          mDisplayName,
          mBitmapData,
          mAddress,
          mAddressType,
          mAddressLabel);
    }
  }

  private Bitmap getPhotoBitmap(byte[] bitmapData) {
    Bitmap bitmap;
    if (bitmapData != null) {
      bitmap = BitmapFactory.decodeByteArray(
          bitmapData,
          0,
          bitmapData.length,
          null);
    } else {
      bitmap = ((BitmapDrawable) mContext.getResources().getDrawable(
          R.drawable.ic_contact_picture_holo_light)).getBitmap();
    }
    return bitmap;
  }

  private void createNavigationShortcutIntent(Uri uri, String displayName,
      byte[] bitmapData, String address, int addressType, String addressLabel) {
    Bitmap bitmap = getPhotoBitmap(bitmapData);

    Uri navigationUri;
    // Make the URI a direct tel: URI so that it will always continue to work
    navigationUri = Uri.parse(SCHEME_NAV + ":q=" + address);
    bitmap = generateNavigationIcon(
        bitmap,
        addressType,
        addressLabel,
        R.drawable.location_directions_holo_dark);

    Intent shortcutIntent = new Intent(Intent.ACTION_VIEW, navigationUri);
    shortcutIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

    Intent intent = new Intent();
    intent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);
    intent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
    intent.putExtra(Intent.EXTRA_SHORTCUT_NAME, displayName);

    mListener.onShortcutIntentCreated(uri, intent);
  }

  private void drawBorder(Canvas canvas, Rect dst) {
    // Darken the border
    final Paint workPaint = new Paint();
    workPaint.setColor(mBorderColor);
    workPaint.setStyle(Paint.Style.STROKE);
    // The stroke is drawn centered on the rect bounds, and since half will be
    // drawn outside the
    // bounds, we need to double the width for it to appear as intended.
    workPaint.setStrokeWidth(mBorderWidth * 2);
    canvas.drawRect(dst, workPaint);
  }

  /**
   * Generates a navigation shortcut icon. Adds an overlay describing the type
   * of the address, and if there is a photo also adds the navigation action
   * icon.
   */
  private Bitmap generateNavigationIcon(Bitmap photo, int phoneType,
      String phoneLabel, int actionResId) {
    final Resources r = mContext.getResources();
    final float density = r.getDisplayMetrics().density;

    Bitmap navigationIcon = ((BitmapDrawable) r.getDrawable(actionResId))
        .getBitmap();

    // Setup the drawing classes
    Bitmap icon = Bitmap.createBitmap(
        mIconSize,
        mIconSize,
        Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(icon);

    // Copy in the photo
    Paint photoPaint = new Paint();
    photoPaint.setDither(true);
    photoPaint.setFilterBitmap(true);
    Rect src = new Rect(0, 0, photo.getWidth(), photo.getHeight());
    Rect dst = new Rect(0, 0, mIconSize, mIconSize);
    canvas.drawBitmap(photo, src, dst, photoPaint);

    drawBorder(canvas, dst);

    // Create an overlay for the phone number type
    CharSequence overlay = StructuredPostal.getTypeLabel(
        r,
        phoneType,
        phoneLabel);

    if (overlay != null) {
      TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG
          | Paint.DEV_KERN_TEXT_FLAG);
      textPaint.setTextSize(r.getDimension(R.dimen.shortcut_overlay_text_size));
      textPaint.setColor(r.getColor(R.color.textColorIconOverlay));
      textPaint.setShadowLayer(
          4f,
          0,
          2f,
          r.getColor(R.color.textColorIconOverlayShadow));

      final FontMetricsInt fmi = textPaint.getFontMetricsInt();

      // First fill in a darker background around the text to be drawn
      final Paint workPaint = new Paint();
      workPaint.setColor(mBorderColor);
      workPaint.setStyle(Paint.Style.FILL);
      final int textPadding = r
          .getDimensionPixelOffset(R.dimen.shortcut_overlay_text_background_padding);
      final int textBandHeight = (fmi.descent - fmi.ascent) + textPadding * 2;
      dst.set(0 + mBorderWidth, mIconSize - textBandHeight, mIconSize
          - mBorderWidth, mIconSize - mBorderWidth);
      canvas.drawRect(dst, workPaint);

      final float sidePadding = mBorderWidth;
      overlay = TextUtils.ellipsize(overlay, textPaint, mIconSize - 2
          * sidePadding, TruncateAt.END);
      final float textWidth = textPaint.measureText(
          overlay,
          0,
          overlay.length());
      canvas.drawText(
          overlay,
          0,
          overlay.length(),
          (mIconSize - textWidth) / 2,
          mIconSize - fmi.descent - textPadding,
          textPaint);
    }

    // Draw the phone action icon as an overlay
    src.set(0, 0, navigationIcon.getWidth(), navigationIcon.getHeight());
    int iconWidth = icon.getWidth();
    dst.set(
        iconWidth - ((int) (20 * density)),
        -1,
        iconWidth,
        ((int) (19 * density)));
    dst.offset(-mBorderWidth, mBorderWidth);
    canvas.drawBitmap(navigationIcon, src, dst, photoPaint);

    canvas.setBitmap(null);

    return icon;
  }
}
