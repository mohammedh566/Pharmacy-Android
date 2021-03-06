package com.apharmacy.app.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.NavUtils;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import com.apharmacy.app.App;
import com.apharmacy.app.R;
import com.apharmacy.app.Utils.Constants;
import com.apharmacy.app.Utils.Prefs;
import com.apharmacy.app.Utils.Utils;
import com.apharmacy.app.controllers.OrderManager;
import com.apharmacy.app.controllers.UserManager;
import com.apharmacy.app.models.Address;
import com.apharmacy.app.models.Order;
import com.apharmacy.app.models.User;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.firebase.client.AuthData;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.firebase.client.ValueEventListener;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import pl.aprilapps.easyphotopicker.DefaultCallback;
import pl.aprilapps.easyphotopicker.EasyImage;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

/**
 * A placeholder fragment containing a simple view.
 */
public class NewOrderFragment extends Fragment {

    public static final String KEY_PRESCRIPTION_URI = "prescription_uri";
    private final String TAG = this.getClass().getSimpleName();
    private Firebase mRef = App.getFirebase();

    private User mUser;

    private CompositeSubscription mCompositeSubscription;

    private File mPrescriptionFile;

    @BindView(R.id.textview_name)
    TextView nameTextView;

    @BindView(R.id.textview_address_line_1)
    TextView addressLine1TextView;

    @BindView(R.id.textview_address_line_2)
    TextView addressLine2TextView;

    @BindView(R.id.textview_landmark)
    TextView landmarkTextView;

    @BindView(R.id.textview_pin)
    TextView pinTextView;

    @BindView(R.id.button_edit_address)
    TextView editAddressButton;


    @BindView(R.id.button_scan)
    Button mScanButton;

    @BindView(R.id.imageview_prescription)
    ImageView prescriptionImageview;

    @BindView(R.id.input_note)
    EditText noteEditText;

/*    @BindView(R.id.button_submit_order)
    ActionProcessButton mSubmitButton;*/

    @BindView(R.id.fab_order)
    FloatingActionButton fabOrderButton;

    // Used for detaching the listeners
    Query mUserRef;
    Query mAddressRef;
    ValueEventListener mUserEventListener;
    ValueEventListener mAddressEventListener;

    String mRxPath = "";

    public NewOrderFragment() {

    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Check if user logged in else go back to login activity
        if (App.getFirebase().getAuth() == null) {
            startActivity(LoginActivity.getInstance(getActivity()));
            getActivity().finish();
            return;
        }

    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {


        View rootView = inflater.inflate(R.layout.fragment_new_order, container, false);
        ButterKnife.bind(this, rootView);


        mCompositeSubscription = new CompositeSubscription();

/*
        disableSubmitButton();
*/
        AuthData authData = mRef.getAuth();

        if (savedInstanceState != null) {
            if (savedInstanceState.getString(KEY_PRESCRIPTION_URI) != null) {
                String prescriptionUri = savedInstanceState.getString(KEY_PRESCRIPTION_URI);
                setmPrescriptionFile(new File(prescriptionUri));
            }
        }

        String uid = authData.getUid();
        mUserRef = mRef.child(Constants.Path.USERS).child(uid);

       mUserEventListener = mUserRef.addValueEventListener(new ValueEventListener() {
           @Override
           public void onDataChange(DataSnapshot dataSnapshot) {
               try {
                   User user = dataSnapshot.getValue(User.class);
                   if (user != null) {
                       populateUser(user);
                       disableOrderCreationIfBanned(user);
                       mUser = user;
                   }
               } catch (Exception e) {
                   showSnackbar("Sorry, Unable to retrive your details");
                   e.printStackTrace();
               }
           }

           @Override
           public void onCancelled(FirebaseError firebaseError) {
               firebaseError.toException().printStackTrace();
                showSnackbar("Sorry, Unable to retrive your details");
           }
       });

        mAddressRef = mRef.child(Constants.Path.ADDRESSES).child(uid).limitToFirst(1);
        mAddressEventListener = mAddressRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                try {
                    dataSnapshot = dataSnapshot.getChildren().iterator().next();
                    Address address = dataSnapshot.getValue(Address.class);
                    if (address != null) {
                        populateAddress(address);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    showSnackbar("Sorry, Unable to retrive your address");
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                firebaseError.toException().printStackTrace();
                showSnackbar("Sorry, Unable to retrive your address");

            }
        });


       // return super.onCreateView(inflater, container, savedInstanceState);
        return rootView;

    }


    @OnClick(R.id.button_edit_address)
    void onEditAddress() {
        startActivity(EditUserActivity.getInstance(getActivity()));
    }

    @OnClick(R.id.button_scan)
    void onScan() {
        Log.d(TAG, "onScan: clicked");

        EasyImage.configuration(getContext())
                .setCopyExistingPicturesToPublicLocation(true)
                .setImagesFolderName("prescriptions");

        EasyImage.openChooserWithGallery(this, "Scan Prescription", 2);
    }

    @OnClick(R.id.imageview_prescription)
    void onPrescriptionClick() {

        if (mPrescriptionFile != null) {
            String uri = mPrescriptionFile.getPath();
            startActivity(ImageViewActivity.getInstance(getActivity(), uri));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        EasyImage.handleActivityResult(requestCode, resultCode, data, getActivity(), new DefaultCallback() {
            @Override
            public void onImagePicked(File file, EasyImage.ImageSource imageSource, int i) {
                Log.d(TAG, "onImagePicked: file Absolute path: " + file.getAbsolutePath());
                Log.d(TAG, "onImagePicked: file  path: " + file.getPath());

                setmPrescriptionFile(file);
                attachImage(file);

                //enable prescription viewer after setting prescription
                prescriptionImageview.setVisibility(View.VISIBLE);


            }

            @Override
            public void onImagePickerError(Exception e, EasyImage.ImageSource source, int type) {
               // super.onImagePickerError(e, source, type);
            }

            @Override
            public void onCanceled(EasyImage.ImageSource source, int type) {
                // delete captured but not used image
                if (source == EasyImage.ImageSource.CAMERA) {
                    File photoFile = EasyImage.lastlyTakenButCanceledPhoto(getContext());
                    if (photoFile != null) photoFile.delete();
                }
            }
        });
    }

    private void setmPrescriptionFile(File file) {
        mPrescriptionFile = file;
        attachImage(file);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                fabShowAndClickable();
            }
        }, 1000);
    }

    private void fabShowAndClickable() {
        fabOrderButton.show();
        fabOrderButton.setClickable(true);

    }

    private void disableOrderCreationIfBanned(User user) {

        if (user.isBanned()) {
            mScanButton.setClickable(false);
            mScanButton.setEnabled(false);
            Snackbar.make(mScanButton, "Sorry, You are not allowed to order", Snackbar.LENGTH_INDEFINITE)
                    .setAction("Contact Pharmacy", new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            String contactNo = getResources().getString(R.string.pharmacy_contact_number);
                            String number = "tel:" + contactNo;

                            Intent callingIntent = new Intent(Intent.ACTION_DIAL, Uri.parse(number));
                            startActivity(callingIntent);
                        }
                    })
            .show();
        } else {
            mScanButton.setClickable(true);
            mScanButton.setEnabled(true);
        }
    }
    private void fabShowLoadingAnimation() {
        fabOrderButton.clearAnimation();
        fabOrderButton.setImageResource(R.drawable.refresh);
        fabOrderButton.setClickable(false);
        Animation roate = AnimationUtils.loadAnimation(App.getContext(), R.anim.loading_roate);
        fabOrderButton.startAnimation(roate);

    }

    private void fabShowFailed() {
        fabOrderButton.clearAnimation();
        fabOrderButton.setImageResource(R.drawable.failed);
        fabOrderButton.setBackgroundTintList(ContextCompat.getColorStateList(getActivity(), R.color.md_red_400));
        fabOrderButton.setClickable(true);

    }

    private void fabShowSuccess() {

        fabOrderButton.clearAnimation();
        fabOrderButton.setImageResource(R.drawable.ok_mark);
        fabOrderButton.setBackgroundTintList(ContextCompat.getColorStateList(getActivity(), R.color.md_green_400));
        fabOrderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               // getActivity().finish();
                NavUtils.navigateUpTo(getActivity(), OrderListActivity.getInstance(getActivity()));
                return;
            }
        });
    }

    private void attachImage(File file) {
        Glide.with(this)
                .load(file)
                .fitCenter()
                .listener(new RequestListener<File, GlideDrawable>() {
                    @Override
                    public boolean onException(Exception e, File model, Target<GlideDrawable> target, boolean isFirstResource) {
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(GlideDrawable resource, File model, Target<GlideDrawable> target, boolean isFromMemoryCache, boolean isFirstResource) {
                        return false;
                    }
                })
                .into(prescriptionImageview);

    }


    @OnClick(R.id.fab_order)
    void submitOrder() {

        //Create order instance
        Order order = new Order();
        order.setNote(noteEditText.getText().toString());
        order.setOrderId(Utils.generateOrderId());


        if (mPrescriptionFile != null) {
            // enable indeterminate mode
/*            mSubmitButton.setMode(ActionProcessButton.Mode.ENDLESS);
            mSubmitButton.setProgress(2);*/
            fabShowLoadingAnimation();


            Observable<String> imageuploadObservable = OrderManager.uploadImage(mPrescriptionFile, order.getOrderId())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread());

            Observable<String> addressKeyObservable = UserManager.getAddressKey();


            Subscription orderSubscription = Observable.zip(imageuploadObservable, addressKeyObservable, (publicId, addressKey) -> {

                Map<String, String> map = new HashMap<String, String>();
                map.put("public_id", publicId);
                map.put("address_key", addressKey);

                return map;
            }).flatMap(map -> {
                String imageId = map.get("public_id");

                Log.d(TAG, "submitOrder: ImageUrl: " + imageId);
                // set order prescription image
                order.setPrescriptionUrl(imageId);

                // set order address
                String addressKey = map.get("address_key");
                order.setAddress(addressKey);


                return OrderManager.createOrder(order);

            }).subscribe(orderKey -> {
                Timber.d("Onsuccess key: %s", orderKey);
            }, throwable -> {
                showSnackbar(throwable.getMessage());
                fabShowFailed();
                if (order.getPrescriptionUrl() != null) {
                    // delete image on order failed
                    OrderManager.deleteImage(order.getPrescriptionUrl())
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(aVoid -> {
                                // image deleted onNext
                                // no operation on onNext because of Void
                            }, throwable1 -> {
                                // image delete failed
                            }, () -> {
                                // image deleted completed
                            });

                }
            }, () -> {
                // Order success
                fabShowSuccess();
               // showSnackbar("Order created " + order.getOrderId());
               showOrderSuccess(order);
                firstTimeGoToList();
            });


            mCompositeSubscription.add(orderSubscription);

        } else if (mPrescriptionFile == null && noteEditText.getText().toString().isEmpty()){

            // User has not entered both medicine details and prescription
           // showSnackbar("Please attach a prescription");
            showSnackbar("Please at least enter medicine details or attach your prescription");

        } else if (mPrescriptionFile == null && !noteEditText.getText().toString().isEmpty()) {
            // if user has not attached but entered medicine details

            showPrescriptionRequiredAndOrder();

        }

    }

    private void showOrderSuccess(Order order) {
        Snackbar.make(landmarkTextView,
                "Order created " + order.getOrderId(), Snackbar.LENGTH_LONG)
                .setAction("View", v -> {
                    startActivity(OrderDetailActivity.getInstance(getActivity(), order.getOrderPath()));
                    getActivity().finish();
                    return;
                }).show();
    }

    public void showPrescriptionRequiredAndOrder() {

        new AlertDialog.Builder(getActivity())
                .setTitle("Prescription required")
                .setMessage("You have to show prescription at the time of delivery. Do you want to order ?")
                .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                    fabShowLoadingAnimation();

                    //Create order instance
                    Order order = new Order();
                    order.setNote(noteEditText.getText().toString());
                    order.setOrderId(Utils.generateOrderId());
                    order.setPrescriptionUrl("");

                    Subscription orderSubscription =
                            Observable.merge(
                                    Utils.isNetworkAvailable(),
                            OrderManager.createOrder(order)
                            )
                            .subscribe(s -> {
                               /* fabShowSuccess();
                                showSnackbar("onnext Order created " + order.getOrderId());*/
                            }, throwable -> {
                                showSnackbar("Order failed. Make sure your are connected to internet.");
                                fabShowFailed();
                            }, () -> {
                                // On order success
                                fabShowSuccess();
                              //  showSnackbar("Order created " + order.getOrderId());
                                showOrderSuccess(order);
                                firstTimeGoToList();
                            });
                    mCompositeSubscription.add(orderSubscription);

                }).setNegativeButton(android.R.string.no, null).show();
    }

    private void firstTimeGoToList() {

        if (Prefs.getInstance(getActivity()).getBoolean(Prefs.Key.IS_FIRST_TIME, true)) {
            // Now no need to go back to list
            Prefs.getInstance().put(Prefs.Key.IS_FIRST_TIME, false);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    startActivity(OrderListActivity.getInstance(getActivity()));
                    getActivity().finish();
                    return;
                }
            }, 1000);

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Timber.d("On destroy called");
        if (mCompositeSubscription != null) {

            mCompositeSubscription.unsubscribe();
        }
        // clear firebase event listeners
        mUserRef.removeEventListener(mUserEventListener);
        mAddressRef.removeEventListener(mAddressEventListener);

    }


    @Override
    public void onStop() {
        super.onStop();

    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPrescriptionFile != null) {
            outState.putString(KEY_PRESCRIPTION_URI, mPrescriptionFile.getPath());
        }
    }

    private void showSnackbar(String message) {

        Snackbar.make(editAddressButton, message , Snackbar.LENGTH_LONG).show();

    }

    private void populateUser(User user) {

        nameTextView.setText(user.getFirstName() + " " + user.getLastName());
    }

    private void populateAddress(Address address) {

        addressLine1TextView.setText(address.getAddressLine1());
        addressLine2TextView.setText(address.getAddressLine2());
        landmarkTextView.setText(address.getLandmark());
        pinTextView.setText(String.valueOf(address.getPin()));
    }
    /*    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_new_order, container, false);
    }*/
}
