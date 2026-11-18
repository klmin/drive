import Document, { Html, Head, Main, NextScript } from 'next/document';
import React from 'react';

class MyDocument extends Document {
    static async getInitialProps(ctx) {
        const initialProps = await Document.getInitialProps(ctx);
        const { locale } = ctx;

        return {
            ...initialProps,
            locale: locale || 'en',
        };
    }

    render() {
        const { locale } = this.props;

        return (
            <Html lang={locale}>
                <Head>
                    {/* <link rel="icon" href="/favicon.ico" /> */}
                    <meta
                        property="description"
                        content="Securely store your files and share them with ease."
                    />
                    <meta name="robots" content="noindex, nofollow" />
                </Head>
                <body>
                    <Main />
                    <NextScript />
                </body>
            </Html>
        );
    }
}

export default MyDocument;
