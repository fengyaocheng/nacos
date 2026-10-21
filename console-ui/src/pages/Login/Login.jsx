/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import React from 'react';
import { Card, Form, Input, Message, ConfigProvider, Field, Button, Dialog } from '@alifd/next';
import { withRouter } from 'react-router-dom';

import './index.scss';
import Header from '../../layouts/Header';
import PropTypes from 'prop-types';
import { consoleLogin, consoleMfaVerify, guide, state } from '../../reducers/base';

const FormItem = Form.Item;

@withRouter
@ConfigProvider.config
class Login extends React.Component {
  static displayName = 'Login';

  static propTypes = {
    locale: PropTypes.object,
    history: PropTypes.object,
  };

  constructor(props) {
    super(props);
    this.state = {
      consoleUiEnable: true,
      guideMsg: '',
      stage: 'LOGIN', // 'LOGIN' | 'MFA_SETUP' | 'MFA_VERIFY'
      challengeId: '',
      username: '',
      secret: '',
      qrCode: '',
      mfaCode: '',
      useRecoveryCode: false,
      showRecoveryDialog: false,
      recoveryCodes: [],
    };
    this.field = new Field(this);
  }

  componentDidMount() {
    if (localStorage.getItem('token')) {
      const [baseUrl] = location.href.split('#');
      location.href = `${baseUrl}#/`;
    }
    this.handleSearch();
  }

  handleSearch = () => {
    state().then(res => {
      if (res?.console_ui_enabled === 'false') {
        this.setState({ consoleUiEnable: true });
        guide().then(res => {
          this.setState({ guideMsg: res?.data });
        });
      } else {
        this.setState({ consoleUiEnable: false });
      }
    });
  };

  handleSubmit = () => {
    const { locale = {} } = this.props;
    this.field.validate((errors, values) => {
      if (errors) {
        return;
      }
      consoleLogin(values)
        .then(res => {
          if (res.status === 'MFA_SETUP_REQUIRED') {
            this.setState({
              stage: 'MFA_SETUP',
              challengeId: res.challengeId,
              secret: res.secret,
              qrCode: res.qrCode,
              username: res.username,
              mfaCode: '',
            });
          } else if (res.status === 'MFA_REQUIRED') {
            this.setState({
              stage: 'MFA_VERIFY',
              challengeId: res.challengeId,
              username: res.username,
              useRecoveryCode: false,
              mfaCode: '',
            });
          } else if (res.accessToken) {
            localStorage.setItem('token', JSON.stringify(res));
            this.props.history.push('/');
          }
        })
        .catch(err => {
          const msg =
            (err && err.response && err.response.data && err.response.data.message) ||
            locale.invalidUsernameOrPassword;
          Message.error({
            content: msg,
          });
        });
    });
  };

  handleMfaVerify = () => {
    const { locale = {} } = this.props;
    const { challengeId, mfaCode, useRecoveryCode } = this.state;
    if (!mfaCode || !mfaCode.trim()) {
      Message.error({
        content: locale.mfaCodeRequired,
      });
      return;
    }
    consoleMfaVerify({
      challengeId,
      code: mfaCode.trim(),
      isRecoveryCode: !!useRecoveryCode,
    })
      .then(res => {
        localStorage.setItem('token', JSON.stringify(res));
        if (res.recoveryCodes && res.recoveryCodes.length > 0) {
          this.setState({
            recoveryCodes: res.recoveryCodes,
            showRecoveryDialog: true,
          });
        } else {
          this.props.history.push('/');
        }
      })
      .catch(err => {
        const msg =
          (err && err.response && err.response.data && err.response.data.message) ||
          locale.invalidUsernameOrPassword;
        Message.error({
          content: msg,
        });
      });
  };

  handleRecoveryConfirm = () => {
    this.setState({ showRecoveryDialog: false });
    this.props.history.push('/');
  };

  copyRecoveryCodes = () => {
    const { recoveryCodes } = this.state;
    const { locale = {} } = this.props;
    const text = recoveryCodes.join('\n');
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text).then(() => {
        Message.success({ content: locale.copySuccess || '已复制到剪贴板' });
      });
    } else {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      Message.success({ content: locale.copySuccess || '已复制到剪贴板' });
    }
  };

  handleBackToLogin = () => {
    this.setState({
      stage: 'LOGIN',
      challengeId: '',
      secret: '',
      qrCode: '',
      mfaCode: '',
      useRecoveryCode: false,
    });
  };

  onKeyDown = event => {
    if (event.key === 'Enter') {
      event.preventDefault();
      event.stopPropagation();
      if (this.state.stage === 'LOGIN') {
        this.handleSubmit();
      } else {
        this.handleMfaVerify();
      }
    }
  };

  renderLoginForm = () => {
    const { locale = {} } = this.props;
    return (
      <Form className="login-form" field={this.field}>
        <FormItem>
          <Input
            {...this.field.init('username', {
              rules: [
                {
                  required: true,
                  message: locale.usernameRequired,
                },
              ],
            })}
            placeholder={locale.pleaseInputUsername}
            onKeyDown={this.onKeyDown}
          />
        </FormItem>
        <FormItem>
          <Input
            htmlType="password"
            placeholder={locale.pleaseInputPassword}
            {...this.field.init('password', {
              rules: [
                {
                  required: true,
                  message: locale.passwordRequired,
                },
              ],
            })}
            onKeyDown={this.onKeyDown}
          />
        </FormItem>
        <FormItem label=" ">
          <Form.Submit onClick={this.handleSubmit}>{locale.submit}</Form.Submit>
        </FormItem>
      </Form>
    );
  };

  renderMfaSetup = () => {
    const { locale = {} } = this.props;
    const { qrCode, secret, mfaCode } = this.state;
    return (
      <div style={{ textAlign: 'center', padding: '10px 0' }}>
        <div style={{ fontSize: 13, color: '#555', marginBottom: 8 }}>{locale.mfaSetupDesc}</div>
        {qrCode && (
          <img
            src={qrCode}
            alt="OTP QR Code"
            style={{
              width: 150,
              height: 150,
              margin: '0 auto 8px auto',
              display: 'block',
              border: '1px solid #e5e5e5',
              padding: 4,
              borderRadius: 4,
            }}
          />
        )}
        <div style={{ fontSize: 12, color: '#888', marginBottom: 15, wordBreak: 'break-all' }}>
          {locale.mfaSecretLabel}
          <strong style={{ color: '#333', marginLeft: 4 }}>{secret}</strong>
        </div>
        <div style={{ marginBottom: 15 }}>
          <Input
            placeholder={locale.mfaVerifyDesc}
            value={mfaCode}
            maxLength={6}
            onChange={val => this.setState({ mfaCode: val })}
            onKeyDown={this.onKeyDown}
            style={{ width: '100%', textAlign: 'center', fontSize: 16, letterSpacing: 4 }}
          />
        </div>
        <Button type="primary" onClick={this.handleMfaVerify} style={{ width: '100%', marginBottom: 12 }}>
          {locale.submit}
        </Button>
        <div>
          <a onClick={this.handleBackToLogin} style={{ fontSize: 12, color: '#1890ff', cursor: 'pointer' }}>
            {locale.backToLogin}
          </a>
        </div>
      </div>
    );
  };

  renderMfaVerify = () => {
    const { locale = {} } = this.props;
    const { useRecoveryCode, mfaCode } = this.state;
    return (
      <div style={{ textAlign: 'center', padding: '15px 0' }}>
        <div style={{ fontSize: 13, color: '#555', marginBottom: 15 }}>
          {useRecoveryCode ? locale.mfaRecoveryDesc : locale.mfaVerifyDesc}
        </div>
        <div style={{ marginBottom: 20 }}>
          <Input
            placeholder={useRecoveryCode ? 'xxxx-xxxx' : '000000'}
            value={mfaCode}
            maxLength={useRecoveryCode ? 16 : 6}
            onChange={val => this.setState({ mfaCode: val })}
            onKeyDown={this.onKeyDown}
            style={{ width: '100%', textAlign: 'center', fontSize: 18, letterSpacing: 3 }}
          />
        </div>
        <Button type="primary" onClick={this.handleMfaVerify} style={{ width: '100%', marginBottom: 15 }}>
          {locale.submit}
        </Button>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 12 }}>
          <a
            onClick={() => this.setState({ useRecoveryCode: !useRecoveryCode, mfaCode: '' })}
            style={{ color: '#1890ff', cursor: 'pointer' }}
          >
            {useRecoveryCode ? locale.useTotpCode : locale.useRecoveryCode}
          </a>
          <a onClick={this.handleBackToLogin} style={{ color: '#888', cursor: 'pointer' }}>
            {locale.backToLogin}
          </a>
        </div>
      </div>
    );
  };

  render() {
    const { locale = {} } = this.props;
    const { consoleUiEnable, guideMsg, stage, showRecoveryDialog, recoveryCodes } = this.state;

    let headerTitle = locale.login;
    if (stage === 'MFA_SETUP') {
      headerTitle = locale.mfaSetupTitle;
    } else if (stage === 'MFA_VERIFY') {
      headerTitle = this.state.useRecoveryCode ? locale.mfaRecoveryTitle : locale.mfaVerifyTitle;
    }

    return (
      <div className="home-page">
        <Header />
        <section
          className="top-section"
          style={{
            background: 'url(img/black_dot.png) repeat',
            backgroundSize: '14px 14px',
          }}
        >
          <div className="vertical-middle product-area">
            <img className="product-logo" src="img/nacos.png" />
            <p className="product-desc">{locale.productDesc}</p>
          </div>
          <div className="animation animation1" />
          <div className="animation animation2" />
          <div className="animation animation3" />
          <div className="animation animation4" />
          <div className="animation animation5" />
          <Card className="login-panel" contentHeight="auto">
            <div className="login-header">{headerTitle}</div>
            {stage === 'LOGIN' && (
              <div className="internal-sys-tip">
                <div>{locale.internalSysTip1}</div>
                <div>{locale.internalSysTip2}</div>
              </div>
            )}
            {!consoleUiEnable && (
              <div>
                {stage === 'LOGIN' && this.renderLoginForm()}
                {stage === 'MFA_SETUP' && this.renderMfaSetup()}
                {stage === 'MFA_VERIFY' && this.renderMfaVerify()}
              </div>
            )}
            {consoleUiEnable && (
              <Message type="notice" style={{ marginTop: 30 }}>
                <div dangerouslySetInnerHTML={{ __html: guideMsg }} />
              </Message>
            )}
          </Card>
        </section>

        <Dialog
          title={locale.recoveryCodesTitle}
          visible={showRecoveryDialog}
          footer={
            <div>
              <Button onClick={this.copyRecoveryCodes} style={{ marginRight: 8 }}>
                {locale.copySuccess ? '复制恢复码' : 'Copy'}
              </Button>
              <Button type="primary" onClick={this.handleRecoveryConfirm}>
                {locale.recoverySavedConfirm}
              </Button>
            </div>
          }
          onClose={this.handleRecoveryConfirm}
        >
          <div style={{ padding: '10px 0' }}>
            <p style={{ color: '#d9363e', marginBottom: 12 }}>{locale.recoveryCodesDesc}</p>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(2, 1fr)',
                gap: '8px',
                background: '#f8f9fa',
                padding: '12px',
                borderRadius: '4px',
                fontFamily: 'monospace',
                fontSize: 14,
                textAlign: 'center',
              }}
            >
              {recoveryCodes.map((code, idx) => (
                <div key={idx} style={{ background: '#fff', border: '1px solid #e9ecef', padding: '4px 8px' }}>
                  {code}
                </div>
              ))}
            </div>
          </div>
        </Dialog>
      </div>
    );
  }
}

export default Login;
