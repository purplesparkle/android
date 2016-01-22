/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.ui.setup;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatRadioButton;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;

import at.bitfire.davdroid.R;
import at.bitfire.davdroid.ui.widget.EditPassword;
import lombok.RequiredArgsConstructor;

public class LoginCredentialsFragment extends Fragment implements CompoundButton.OnCheckedChangeListener {

    AppCompatRadioButton radioUseEmail;
    LinearLayout emailDetails;
    EditText editEmailAddress;
    EditPassword editEmailPassword;

    AppCompatRadioButton radioUseURL;
    LinearLayout urlDetails;
    EditText editBaseURL, editUserName;
    EditPassword editUrlPassword;
    AppCompatCheckBox checkPreemptiveAuth;


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.login_credentials_fragment, container, false);

        radioUseEmail = (AppCompatRadioButton)v.findViewById(R.id.login_type_email);
        emailDetails = (LinearLayout)v.findViewById(R.id.login_type_email_details);
        editEmailAddress = (EditText)v.findViewById(R.id.email_address);
        editEmailPassword = (EditPassword)v.findViewById(R.id.email_password);

        radioUseURL = (AppCompatRadioButton)v.findViewById(R.id.login_type_url);
        urlDetails = (LinearLayout)v.findViewById(R.id.login_type_url_details);
        editBaseURL = (EditText)v.findViewById(R.id.base_url);
        editUserName = (EditText)v.findViewById(R.id.user_name);
        editUrlPassword = (EditPassword)v.findViewById(R.id.url_password);
        checkPreemptiveAuth = (AppCompatCheckBox)v.findViewById(R.id.preemptive_auth);

        radioUseEmail.setOnCheckedChangeListener(this);
        radioUseURL.setOnCheckedChangeListener(this);

        if (savedInstanceState == null)
            radioUseEmail.setChecked(true);

        final Button login = (Button)v.findViewById(R.id.login);
        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LoginCredentials credentials = validateLoginData();
                if (credentials != null) {
                    // login data OK, continue with DetectConfigurationFragment
                    Bundle args = new Bundle(1);
                    args.putParcelable(DetectConfigurationFragment.ARG_LOGIN_CREDENTIALS, credentials);

                    DialogFragment dialog = new DetectConfigurationFragment();
                    dialog.setArguments(args);
                    dialog.show(getFragmentManager(), DetectConfigurationFragment.class.getName());
                }
            }
        });

        return v;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            boolean loginByEmail = buttonView == radioUseEmail;
            emailDetails.setVisibility(loginByEmail ? View.VISIBLE : View.GONE);
            urlDetails.setVisibility(loginByEmail ? View.GONE : View.VISIBLE);
        }
    }

    protected LoginCredentials validateLoginData() {
        if (radioUseEmail.isChecked()) {
            URI uri = null;
            boolean valid = true;

            String email = editEmailAddress.getText().toString();
            if (!email.matches(".+@.+")) {
                editEmailAddress.setError(getString(R.string.login_email_address_error));
                valid = false;
            } else
                try {
                    uri = new URI("mailto", email, null);
                } catch (URISyntaxException e) {
                    editEmailAddress.setError(e.getLocalizedMessage());
                    valid = false;
                }

            String password = editEmailPassword.getText().toString();
            if (password.isEmpty()) {
                editEmailPassword.setError(getString(R.string.login_password_required));
                valid = false;
            }

            return valid ? new LoginCredentials(uri, email, password, true) : null;

        } else if (radioUseURL.isChecked()) {
            URI uri = null;
            boolean valid = true;

            String host = null, path = null;
            int port = -1;

            Uri baseUrl = Uri.parse(editBaseURL.getText().toString());
            String scheme = baseUrl.getScheme();
            if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                host = IDN.toASCII(baseUrl.getHost());
                if (host.isEmpty()) {
                    editBaseURL.setError(getString(R.string.login_url_host_name_required));
                    valid = false;
                }

                path = baseUrl.getEncodedPath();
                port = baseUrl.getPort();
                try {
                    uri = new URI(baseUrl.getScheme(), null, host, port, path, null, null);
                } catch (URISyntaxException e) {
                    editBaseURL.setError(e.getLocalizedMessage());
                    valid = false;
                }
            } else {
                editBaseURL.setError(getString(R.string.login_url_must_be_http_or_https));
                valid = false;
            }

            String userName = editUserName.getText().toString();
            if (userName.isEmpty()) {
                editUserName.setError(getString(R.string.login_user_name_required));
                valid = false;
            }

            String password = editUrlPassword.getText().toString();
            if (password.isEmpty()) {
                editUrlPassword.setError(getString(R.string.login_password_required));
                valid = false;
            }

            return valid ? new LoginCredentials(uri, userName, password, checkPreemptiveAuth.isChecked()) : null;
        }

        return null;
    }


    @RequiredArgsConstructor
    public static class LoginCredentials implements Parcelable {
        public final URI uri;
        public final String userName, password;
        public final boolean authPreemptive;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeSerializable(uri);
            dest.writeString(userName);
            dest.writeString(password);
            dest.writeInt(authPreemptive ? 1 : 0);
        }

        public static final Parcelable.Creator CREATOR = new Parcelable.Creator<LoginCredentials>() {
            @Override
            public LoginCredentials createFromParcel(Parcel source) {
                LoginCredentials credentials = new LoginCredentials(
                        (URI)source.readSerializable(),
                        source.readString(), source.readString(),
                        source.readInt() != 0 ? true : false
                );
                return null;
            }

            @Override
            public LoginCredentials[] newArray(int size) {
                return new LoginCredentials[0];
            }
        };
    }

}