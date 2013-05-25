package com.antsapps.directnavshortcut;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;

import com.antsapps.directnavshortcut.ShortcutIntentBuilder.OnShortcutIntentCreatedListener;

public class CreateShortcutActivity extends Activity implements
    OnShortcutIntentCreatedListener {

  private static final int REQUEST_ADDRESS = 23;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Intent intent = new Intent(Intent.ACTION_PICK);
    intent
        .setType(ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_TYPE);
    startActivityForResult(intent, REQUEST_ADDRESS);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // Check which request it is that we're responding to
    if (requestCode == REQUEST_ADDRESS) {
      // Make sure the request was successful
      if (resultCode == Activity.RESULT_OK) {
        // Get the URI that points to the selected contact
        Uri contactUri = data.getData();
        new ShortcutIntentBuilder(this, this)
            .createNavigationShortcutIntent(contactUri);
      } else {
        finish();
      }
    } else {
      finish();
    }
  }

  @Override
  public void onShortcutIntentCreated(Uri uri, Intent shortcutIntent) {
    setResult(RESULT_OK, shortcutIntent);
    finish();
  }
}
