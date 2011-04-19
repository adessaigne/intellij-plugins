package com.intellij.lang.javascript.flex.run;

import com.intellij.execution.ExecutionBundle;
import com.intellij.lang.javascript.flex.FlexUtils;
import com.intellij.lang.javascript.flex.ModulesComboboxWrapper;
import com.intellij.lang.javascript.flex.build.FlexCompilerSettingsEditor;
import com.intellij.lang.javascript.flex.sdk.FlexSdkComboBoxWithBrowseButton;
import com.intellij.lang.javascript.refactoring.ui.JSReferenceEditor;
import com.intellij.lang.javascript.ui.JSClassChooserDialog;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.ComboboxWithBrowseButton;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static com.intellij.lang.javascript.flex.run.AirMobileRunnerParameters.AirMobileDebugTransport;
import static com.intellij.lang.javascript.flex.run.AirMobileRunnerParameters.AirMobileRunMode;
import static com.intellij.lang.javascript.flex.run.AirMobileRunnerParameters.AirMobileRunTarget;

public class AirMobileRunConfigurationForm extends SettingsEditor<AirMobileRunConfiguration> {

  private final Project myProject;
  private ModulesComboboxWrapper myModulesComboboxWrapper;
  private JSClassChooserDialog.PublicInheritor myMainClassFilter;

  private JPanel myMainPanel;
  private JComboBox myModuleComboBox;

  private JRadioButton myOnEmulatorRadioButton;
  private JRadioButton myOnAndroidDeviceRadioButton;
  private JRadioButton myOnIOSDeviceRadioButton;

  private JPanel myEmulatorScreenSizePanel;
  private JComboBox myEmulatorCombo;
  private JTextField myScreenWidth;
  private JTextField myScreenHeight;
  private JTextField myFullScreenWidth;

  private JPanel myDebugTransportPanel;
  private JRadioButton myDebugViaNetworkRadioButton;
  private JRadioButton myDebugViaUSBRadioButton;
  private JTextField myUsbDebugPortTextField;

  private JTextField myFullScreenHeight;
  private JRadioButton myAirDescriptorRadioButton;
  private JRadioButton myMainClassRadioButton;
  private LabeledComponent<ComboboxWithBrowseButton> myApplicationDescriptorComponent;
  private LabeledComponent<TextFieldWithBrowseButton> myRootDirectoryComponent;
  private LabeledComponent<JSReferenceEditor> myMainClassComponent;
  private LabeledComponent<RawCommandLineEditor> myAdlOptionsComponent;
  private FlexSdkComboBoxWithBrowseButton myDebuggerSdkCombo;

  public AirMobileRunConfigurationForm(final Project project) {
    myProject = project;
    myModulesComboboxWrapper = new ModulesComboboxWrapper(myModuleComboBox);

    myModuleComboBox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Module module = myModulesComboboxWrapper.getSelectedModule();
        AirRunConfigurationForm.resetAirRootDirPath(module, myRootDirectoryComponent);
        myDebuggerSdkCombo.setModuleSdk(module == null ? null : FlexUtils.getFlexSdkForFlexModuleOrItsFlexFacets(module));
        AirRunConfigurationForm.updateMainClassField(myProject, myModulesComboboxWrapper, myMainClassComponent, myMainClassFilter);
      }
    });

    initWhatToLaunchRadioButtons();
    AirRunConfigurationForm.initAppDescriptorComponent(myProject, myApplicationDescriptorComponent, myModulesComboboxWrapper,
                                                       myRootDirectoryComponent);

    myRootDirectoryComponent.getComponent().addBrowseFolderListener("AIR Application Root Directory", null, project,
                                                                    new FileChooserDescriptor(false, true, false, false, false, false));

    myAdlOptionsComponent.getComponent()
      .setDialogCaption(StringUtil.capitalizeWords(myAdlOptionsComponent.getRawText(), true));

    initEmulatorRelatedControls();

    myOnIOSDeviceRadioButton.setVisible(false); // until supported

    final ActionListener debugTransportListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateDebugTransportRelatedControls();
      }
    };
    myDebugViaNetworkRadioButton.addActionListener(debugTransportListener);
    myDebugViaUSBRadioButton.addActionListener(debugTransportListener);

    myUsbDebugPortTextField.setVisible(false); // just thought that nobody needs non-standard port, so let's UI be lighter. Remove this line on first request.

    myDebuggerSdkCombo.showModuleSdk(true);

    updateControls();
    AirRunConfigurationForm.updateMainClassField(myProject, myModulesComboboxWrapper, myMainClassComponent, myMainClassFilter);
  }

  private void initEmulatorRelatedControls() {
    myEmulatorCombo.setModel(new DefaultComboBoxModel(AirMobileRunnerParameters.Emulator.values()));

    myEmulatorCombo.setRenderer(new DefaultListCellRenderer() {
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        return super.getListCellRendererComponent(list, ((AirMobileRunnerParameters.Emulator)value).name, index, isSelected, cellHasFocus);
      }
    });

    myEmulatorCombo.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateEmulatorRelatedControls();
      }
    });

    final ActionListener targetDeviceListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();

        if (myOnEmulatorRadioButton.isSelected()) {
          IdeFocusManager.getInstance(myProject).requestFocus(myEmulatorCombo, true);
        }
      }
    };

    myOnEmulatorRadioButton.addActionListener(targetDeviceListener);
    myOnAndroidDeviceRadioButton.addActionListener(targetDeviceListener);
    myOnIOSDeviceRadioButton.addActionListener(targetDeviceListener);
  }

  private void initWhatToLaunchRadioButtons() {
    final ActionListener actionListener = new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        updateControls();
        final JComboBox comboBox = myApplicationDescriptorComponent.getComponent().getComboBox();
        final Component toFocus = comboBox.isEnabled() ? comboBox : myMainClassComponent.getComponent().getChildComponent();
        IdeFocusManager.getInstance(myProject).requestFocus(toFocus, true);
      }
    };
    myAirDescriptorRadioButton.addActionListener(actionListener);
    myMainClassRadioButton.addActionListener(actionListener);
  }


  @NotNull
  protected JComponent createEditor() {
    return myMainPanel;
  }


  private void updateControls() {
    myEmulatorCombo.setEnabled(myOnEmulatorRadioButton.isSelected());
    UIUtil.setEnabled(myEmulatorScreenSizePanel, myOnEmulatorRadioButton.isSelected(), true);

    if (myOnEmulatorRadioButton.isSelected()) {
      updateEmulatorRelatedControls();
    }
    updateDebugTransportRelatedControls();

    myApplicationDescriptorComponent.setVisible(myAirDescriptorRadioButton.isSelected());
    myRootDirectoryComponent.setVisible(myAirDescriptorRadioButton.isSelected());
    myMainClassComponent.setVisible(myMainClassRadioButton.isSelected());

    myAdlOptionsComponent.setVisible(myOnEmulatorRadioButton.isSelected());
  }

  private void updateEmulatorRelatedControls() {
    final AirMobileRunnerParameters.Emulator emulator = (AirMobileRunnerParameters.Emulator)myEmulatorCombo.getSelectedItem();
    if (emulator.adlAlias == null) {
      myScreenWidth.setEditable(true);
      myScreenHeight.setEditable(true);
      myFullScreenWidth.setEditable(true);
      myFullScreenHeight.setEditable(true);
    }
    else {
      myScreenWidth.setEditable(false);
      myScreenHeight.setEditable(false);
      myFullScreenWidth.setEditable(false);
      myFullScreenHeight.setEditable(false);
      myScreenWidth.setText(String.valueOf(emulator.screenWidth));
      myScreenHeight.setText(String.valueOf(emulator.screenHeight));
      myFullScreenWidth.setText(String.valueOf(emulator.fullScreenWidth));
      myFullScreenHeight.setText(String.valueOf(emulator.fullScreenHeight));
    }
  }

  private void updateDebugTransportRelatedControls() {
    final boolean enabled = !myOnEmulatorRadioButton.isSelected();
    UIUtil.setEnabled(myDebugTransportPanel, enabled, true);
    if (enabled) {
      myUsbDebugPortTextField.setEnabled(myDebugViaUSBRadioButton.isSelected());
    }
  }

  protected void resetEditorFrom(final AirMobileRunConfiguration config) {
    final AirMobileRunnerParameters params = config.getRunnerParameters();

    myModulesComboboxWrapper.configure(myProject, params.getModuleName());

    myOnEmulatorRadioButton.setSelected(params.getAirMobileRunTarget() == AirMobileRunTarget.Emulator);
    myOnAndroidDeviceRadioButton.setSelected(params.getAirMobileRunTarget() == AirMobileRunTarget.AndroidDevice);
    myOnIOSDeviceRadioButton.setSelected(params.getAirMobileRunTarget() == AirMobileRunTarget.iOSDevice);
    myEmulatorCombo.setSelectedItem(params.getEmulator());
    if (params.getEmulator().adlAlias == null) {
      myScreenWidth.setText(String.valueOf(params.getScreenWidth()));
      myScreenHeight.setText(String.valueOf(params.getScreenHeight()));
      myFullScreenWidth.setText(String.valueOf(params.getFullScreenWidth()));
      myFullScreenHeight.setText(String.valueOf(params.getFullScreenHeight()));
    }

    myDebugViaNetworkRadioButton.setSelected(params.getDebugTransport() == AirMobileDebugTransport.Network);
    myDebugViaUSBRadioButton.setSelected(params.getDebugTransport() == AirMobileDebugTransport.USB);
    myUsbDebugPortTextField.setText(String.valueOf(params.getUsbDebugPort()));

    myAirDescriptorRadioButton.setSelected(params.getAirMobileRunMode() == AirMobileRunMode.AppDescriptor);
    myMainClassRadioButton.setSelected(params.getAirMobileRunMode() == AirMobileRunMode.MainClass);
    myApplicationDescriptorComponent.getComponent().getComboBox().getEditor()
      .setItem(FileUtil.toSystemDependentName(params.getAirDescriptorPath()));
    myRootDirectoryComponent.getComponent().setText(FileUtil.toSystemDependentName(params.getAirRootDirPath()));
    myMainClassComponent.getComponent().setText(params.getMainClassName());
    myAdlOptionsComponent.getComponent().setText(params.getAdlOptions());
    myAdlOptionsComponent.getComponent().setText(params.getAdlOptions());

    final Module module = myModulesComboboxWrapper.getSelectedModule();
    myDebuggerSdkCombo.setModuleSdk(module == null ? null : FlexUtils.getFlexSdkForFlexModuleOrItsFlexFacets(module));
    myDebuggerSdkCombo.setSelectedSdkRaw(params.getDebuggerSdkRaw());

    updateControls();
  }

  protected void applyEditorTo(final AirMobileRunConfiguration config) throws ConfigurationException {
    final AirMobileRunnerParameters params = config.getRunnerParameters();

    params.setModuleName(myModulesComboboxWrapper.getSelectedText());
    params.setAirMobileRunTarget(myOnEmulatorRadioButton.isSelected()
                                 ? AirMobileRunTarget.Emulator
                                 : myOnAndroidDeviceRadioButton.isSelected()
                                   ? AirMobileRunTarget.AndroidDevice
                                   : AirMobileRunTarget.iOSDevice);

    final AirMobileRunnerParameters.Emulator emulator = (AirMobileRunnerParameters.Emulator)myEmulatorCombo.getSelectedItem();
    params.setEmulator(emulator);
    if (emulator.adlAlias == null) {
      try {
        params.setScreenWidth(Integer.parseInt(myScreenWidth.getText()));
        params.setScreenHeight(Integer.parseInt(myScreenHeight.getText()));
        params.setFullScreenWidth(Integer.parseInt(myFullScreenWidth.getText()));
        params.setFullScreenHeight(Integer.parseInt(myFullScreenHeight.getText()));
      }
      catch (NumberFormatException e) {/**/}
    }

    params.setDebugTransport(myDebugViaNetworkRadioButton.isSelected()
                             ? AirMobileDebugTransport.Network
                             : AirMobileDebugTransport.USB);
    try {
      final int port = Integer.parseInt(myUsbDebugPortTextField.getText().trim());
      if (port > 0 && port < 65535) {
        params.setUsbDebugPort(port);
      }
    }
    catch (NumberFormatException ignore) {/*ignore*/}

    params.setAirMobileRunMode(myAirDescriptorRadioButton.isSelected()
                               ? AirMobileRunMode.AppDescriptor
                               : AirMobileRunMode.MainClass);
    params.setAirDescriptorPath(
      FileUtil.toSystemIndependentName((String)myApplicationDescriptorComponent.getComponent().getComboBox().getEditor().getItem()).trim());
    params.setAirRootDirPath(FileUtil.toSystemIndependentName(myRootDirectoryComponent.getComponent().getText().trim()));
    params.setMainClassName(myMainClassComponent.getComponent().getText().trim());
    params.setAdlOptions(myAdlOptionsComponent.getComponent().getText().trim());
    params.setDebuggerSdkRaw(myDebuggerSdkCombo.getSelectedSdkRaw());
  }

  protected void disposeEditor() {
  }

  private void createUIComponents() {
    myDebuggerSdkCombo = new FlexSdkComboBoxWithBrowseButton(FlexSdkComboBoxWithBrowseButton.FLEX_RELATED_SDK);
    myMainClassFilter = new JSClassChooserDialog.PublicInheritor(myProject, FlexCompilerSettingsEditor.SPRITE_CLASS_NAME, null, true);
    myMainClassComponent = LabeledComponent.create(JSReferenceEditor.forClassName("", myProject, null, GlobalSearchScope.EMPTY_SCOPE, null,
                                                                                  myMainClassFilter, ExecutionBundle
      .message("choose.main.class.dialog.title")), "");
  }
}
